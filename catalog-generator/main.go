// Catalog generator for the Grafana Alloy JetBrains plugin.
//
// Walks `component.AllNames()`, reflects each component's Args and Exports
// types using Alloy's own `alloy:"..."` struct-tag parser, and writes a JSON
// catalog to stdout.
//
// This file is designed to run **from inside a checkout of the Alloy repo at
// a pinned release tag**, because:
//
//   - `internal/component` is an `internal/` package and can only be imported
//     from within the github.com/grafana/alloy module tree;
//   - Alloy's go.mod has ~24 `replace` directives that don't transit to
//     downstream consumers, so a standalone module would fail to resolve deps.
//
// The `build-catalog.sh` script next to this file does the plumbing:
//
//   1. Stage this file into ALLOY_REPO/internal/cmd/catalog-generator/
//   2. `go run` it against Alloy's own go.mod
//   3. Write the JSON into this plugin's resources
//
// Regenerate when the Alloy pin in build-catalog.sh changes.
package main

import (
	"encoding/json"
	"fmt"
	"os"
	"reflect"
	"sort"
	"strings"
	"time"

	"github.com/grafana/alloy/internal/component"
	_ "github.com/grafana/alloy/internal/component/all" // side-effect: registers every component
	"github.com/grafana/alloy/internal/component/metadata"
	"github.com/grafana/alloy/internal/featuregate"

	"github.com/grafana/alloy/internal/cmd/catalog-generator/syntaxtags"
)

// -----------------------------------------------------------------------------
// JSON shape — must stay in lockstep with the Kotlin-side data classes.
// -----------------------------------------------------------------------------

type Catalog struct {
	AlloyVersion string      `json:"alloyVersion"`
	GeneratedAt  string      `json:"generatedAt"`
	Components   []Component `json:"components"`
}

type Component struct {
	Name              string   `json:"name"`
	Namespace         string   `json:"namespace"`
	Stability         string   `json:"stability"`
	Community         bool     `json:"community"`
	DocsURL           string   `json:"docsUrl"`
	Args              []Arg    `json:"args"`
	Blocks            []Block  `json:"blocks"`
	Exports           []Export `json:"exports"`
	AcceptedPortTypes []string `json:"acceptedPortTypes"`
	ExportedPortTypes []string `json:"exportedPortTypes"`
}

type Arg struct {
	Name     string `json:"name"`
	GoType   string `json:"goType"`
	Required bool   `json:"required"`
}

type Block struct {
	Name     string  `json:"name"`
	Optional bool    `json:"optional"`
	Repeated bool    `json:"repeated"`
	Label    bool    `json:"label"` // whether the nested block has a labeled variant
	Args     []Arg   `json:"args"`
	Blocks   []Block `json:"blocks"`
}

type Export struct {
	Name     string `json:"name"`
	GoType   string `json:"goType"`
	PortType string `json:"portType,omitempty"`
}

func main() {
	alloyVersion := os.Getenv("ALLOY_VERSION")
	if alloyVersion == "" {
		alloyVersion = "unknown"
	}

	names := component.AllNames()
	sort.Strings(names)

	out := Catalog{
		AlloyVersion: alloyVersion,
		GeneratedAt:  time.Now().UTC().Format(time.RFC3339),
		Components:   make([]Component, 0, len(names)),
	}

	for _, name := range names {
		reg, ok := component.Get(name)
		if !ok {
			fmt.Fprintf(os.Stderr, "WARN: registered name %q missing from registry\n", name)
			continue
		}
		c, err := buildComponent(name, reg)
		if err != nil {
			fmt.Fprintf(os.Stderr, "WARN: skipping %q: %v\n", name, err)
			continue
		}
		out.Components = append(out.Components, c)
	}

	enc := json.NewEncoder(os.Stdout)
	enc.SetIndent("", "  ")
	if err := enc.Encode(out); err != nil {
		fmt.Fprintf(os.Stderr, "ERROR: %v\n", err)
		os.Exit(1)
	}
	fmt.Fprintf(os.Stderr, "Wrote %d components (alloy=%s)\n", len(out.Components), alloyVersion)
}

func buildComponent(name string, reg component.Registration) (c Component, err error) {
	defer func() {
		if r := recover(); r != nil {
			err = fmt.Errorf("panic while reflecting: %v", r)
		}
	}()

	c.Name = name
	ns := strings.SplitN(name, ".", 2)[0]
	c.Namespace = ns
	c.Community = reg.Community
	c.Stability = stabilityString(reg.Stability)
	c.DocsURL = "https://grafana.com/docs/alloy/latest/reference/components/" + ns + "/" + name + "/"

	argsType := reflect.TypeOf(reg.Args)
	if argsType == nil || derefType(argsType).Kind() != reflect.Struct {
		c.Args = []Arg{}
		c.Blocks = []Block{}
	} else {
		c.Args, c.Blocks = walk(derefType(argsType), map[reflect.Type]bool{})
	}

	if reg.Exports != nil {
		expType := reflect.TypeOf(reg.Exports)
		c.Exports = buildExports(derefType(expType))
	} else {
		c.Exports = []Export{}
	}

	if meta, mErr := metadata.ForComponent(name); mErr == nil {
		c.AcceptedPortTypes = portTypeNames(meta.AllTypesAccepted())
		c.ExportedPortTypes = portTypeNames(meta.AllTypesExported())
	} else {
		c.AcceptedPortTypes = []string{}
		c.ExportedPortTypes = []string{}
	}

	for i := range c.Exports {
		if c.Exports[i].PortType == "" && len(c.ExportedPortTypes) == 1 {
			c.Exports[i].PortType = c.ExportedPortTypes[0]
		}
	}

	return c, nil
}

// -----------------------------------------------------------------------------
// Reflection over `alloy:` struct tags.
// -----------------------------------------------------------------------------

func walk(structType reflect.Type, seen map[reflect.Type]bool) ([]Arg, []Block) {
	if structType.Kind() != reflect.Struct {
		return nil, nil
	}
	if seen[structType] {
		// Self-referential struct (some component configs embed themselves for
		// recursive rules). Stop recursing; the JSON consumer will see this as
		// a block with empty nested blocks, which is an acceptable degradation.
		return nil, nil
	}
	seen[structType] = true
	defer func() { delete(seen, structType) }()

	var (
		args   []Arg
		blocks []Block
	)
	for _, f := range syntaxtags.Get(structType) {
		if f.IsLabel() {
			continue
		}
		fieldType := structType.FieldByIndex(f.Index).Type

		switch {
		case f.IsAttr():
			args = append(args, Arg{
				Name:     strings.Join(f.Name, "."),
				GoType:   renderType(fieldType),
				Required: !f.IsOptional(),
			})

		case f.IsBlock() || f.IsEnum():
			inner := derefType(fieldType)
			repeated := fieldType.Kind() == reflect.Slice || fieldType.Kind() == reflect.Array
			if repeated {
				inner = derefType(fieldType.Elem())
			}
			labeled := hasLabelField(inner)
			var nestedArgs []Arg
			var nestedBlocks []Block
			if inner.Kind() == reflect.Struct {
				nestedArgs, nestedBlocks = walk(inner, seen)
			}
			blocks = append(blocks, Block{
				Name:     strings.Join(f.Name, "."),
				Optional: f.IsOptional(),
				Repeated: repeated,
				Label:    labeled,
				Args:     nestedArgs,
				Blocks:   nestedBlocks,
			})
		}
	}
	return args, blocks
}

func buildExports(t reflect.Type) []Export {
	if t.Kind() != reflect.Struct {
		return []Export{}
	}
	out := []Export{}
	for _, f := range syntaxtags.Get(t) {
		if !f.IsAttr() {
			continue
		}
		ft := t.FieldByIndex(f.Index).Type
		out = append(out, Export{
			Name:   strings.Join(f.Name, "."),
			GoType: renderType(ft),
		})
	}
	return out
}

// -----------------------------------------------------------------------------
// Helpers.
// -----------------------------------------------------------------------------

func derefType(t reflect.Type) reflect.Type {
	for t != nil && t.Kind() == reflect.Pointer {
		t = t.Elem()
	}
	return t
}

func hasLabelField(structType reflect.Type) bool {
	if structType.Kind() != reflect.Struct {
		return false
	}
	for _, f := range syntaxtags.Get(structType) {
		if f.IsLabel() {
			return true
		}
	}
	return false
}

func stabilityString(s featuregate.Stability) string {
	switch s {
	case featuregate.StabilityGenerallyAvailable:
		return "generally-available"
	case featuregate.StabilityPublicPreview:
		return "public-preview"
	case featuregate.StabilityExperimental:
		return "experimental"
	}
	return "undefined"
}

func portTypeNames(types []metadata.Type) []string {
	out := make([]string, 0, len(types))
	for _, t := range types {
		out = append(out, t.Name)
	}
	sort.Strings(out)
	return out
}

func renderType(t reflect.Type) string {
	if t == nil {
		return ""
	}
	switch t.Kind() {
	case reflect.Pointer:
		return "*" + renderType(t.Elem())
	case reflect.Slice:
		return "[]" + renderType(t.Elem())
	case reflect.Array:
		return fmt.Sprintf("[%d]%s", t.Len(), renderType(t.Elem()))
	case reflect.Map:
		return "map[" + renderType(t.Key()) + "]" + renderType(t.Elem())
	case reflect.Struct:
		if t.Name() == "" {
			return "struct"
		}
		return t.String()
	case reflect.Interface:
		if t.Name() == "" {
			return "any"
		}
		return t.String()
	}
	return t.String()
}
