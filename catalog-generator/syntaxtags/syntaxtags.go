// Vendored verbatim from github.com/grafana/alloy (Apache-2.0).
// Source: syntax/internal/syntaxtags/syntaxtags.go at v1.9.2.
// Copied because the upstream lives in a sibling submodule's internal/ tree
// and the main alloy module cannot import it.

package syntaxtags

import (
	"fmt"
	"reflect"
	"strings"
)

type Flags uint

const (
	FlagAttr Flags = 1 << iota
	FlagBlock
	FlagEnum
	FlagOptional
	FlagLabel
	FlagSquash
)

func (f Flags) String() string {
	attrs := make([]string, 0, 5)
	if f&FlagAttr != 0 {
		attrs = append(attrs, "attr")
	}
	if f&FlagBlock != 0 {
		attrs = append(attrs, "block")
	}
	if f&FlagEnum != 0 {
		attrs = append(attrs, "enum")
	}
	if f&FlagOptional != 0 {
		attrs = append(attrs, "optional")
	}
	if f&FlagLabel != 0 {
		attrs = append(attrs, "label")
	}
	if f&FlagSquash != 0 {
		attrs = append(attrs, "squash")
	}
	return fmt.Sprintf("Flags(%s)", strings.Join(attrs, ","))
}

func (f Flags) GoString() string { return f.String() }

type Field struct {
	Name  []string
	Index []int
	Flags Flags
}

func (f Field) IsAttr() bool     { return f.Flags&FlagAttr != 0 }
func (f Field) IsBlock() bool    { return f.Flags&FlagBlock != 0 }
func (f Field) IsEnum() bool     { return f.Flags&FlagEnum != 0 }
func (f Field) IsOptional() bool { return f.Flags&FlagOptional != 0 }
func (f Field) IsLabel() bool    { return f.Flags&FlagLabel != 0 }

func Get(ty reflect.Type) []Field {
	if k := ty.Kind(); k != reflect.Struct {
		panic(fmt.Sprintf("syntaxtags: Get requires struct kind, got %s", k))
	}

	var (
		fields         []Field
		usedNames      = make(map[string][]int)
		usedLabelField = []int(nil)
	)

	for _, field := range reflect.VisibleFields(ty) {
		if field.Anonymous {
			panic(fmt.Sprintf("syntax: anonymous fields not supported %s", printPathToField(ty, field.Index)))
		}

		tag, tagged := field.Tag.Lookup("alloy")
		if !tagged {
			continue
		}
		if !field.IsExported() {
			panic(fmt.Sprintf("syntax: alloy tag found on unexported field at %s", printPathToField(ty, field.Index)))
		}

		options := strings.SplitN(tag, ",", 2)
		if len(options) != 2 {
			panic(fmt.Sprintf("syntax: field %s tag is missing options", printPathToField(ty, field.Index)))
		}

		fullName := options[0]
		tf := Field{Name: strings.Split(fullName, "."), Index: field.Index}

		if first, used := usedNames[fullName]; used && fullName != "" {
			panic(fmt.Sprintf("syntax: field name %s already used by %s", fullName, printPathToField(ty, first)))
		}
		usedNames[fullName] = tf.Index

		flags, ok := parseFlags(options[1])
		if !ok {
			panic(fmt.Sprintf("syntax: unrecognized alloy tag format %q at %s", tag, printPathToField(ty, tf.Index)))
		}
		tf.Flags = flags

		if len(tf.Name) > 1 && tf.Flags&(FlagBlock|FlagEnum) == 0 {
			panic(fmt.Sprintf("syntax: field names with `.` may only be used by blocks or enums (found at %s)", printPathToField(ty, tf.Index)))
		}

		if tf.Flags&FlagEnum != 0 {
			if err := validateEnum(field); err != nil {
				panic(err)
			}
		}

		if tf.Flags&FlagLabel != 0 {
			if fullName != "" {
				panic(fmt.Sprintf("syntax: label field at %s must not have a name", printPathToField(ty, tf.Index)))
			}
			if field.Type.Kind() != reflect.String {
				panic(fmt.Sprintf("syntax: label field at %s must be a string", printPathToField(ty, tf.Index)))
			}
			if usedLabelField != nil {
				panic(fmt.Sprintf("syntax: label field already used by %s", printPathToField(ty, tf.Index)))
			}
			usedLabelField = tf.Index
		}

		if tf.Flags&FlagSquash != 0 {
			if fullName != "" {
				panic(fmt.Sprintf("syntax: squash field at %s must not have a name", printPathToField(ty, tf.Index)))
			}
			innerType := deferenceType(field.Type)
			if !isStructType(innerType) {
				panic(fmt.Sprintf("syntaxtags: squash field requires struct, got %s", innerType))
			}
			for _, innerField := range Get(innerType) {
				fields = append(fields, Field{
					Name:  innerField.Name,
					Index: append(field.Index, innerField.Index...),
					Flags: innerField.Flags,
				})
			}
			continue
		}

		if fullName == "" && tf.Flags&(FlagLabel|FlagSquash) == 0 {
			panic(fmt.Sprintf("syntaxtags: non-empty field name required at %s", printPathToField(ty, tf.Index)))
		}

		fields = append(fields, tf)
	}

	return fields
}

func parseFlags(input string) (f Flags, ok bool) {
	switch input {
	case "attr":
		f |= FlagAttr
	case "attr,optional":
		f |= FlagAttr | FlagOptional
	case "block":
		f |= FlagBlock
	case "block,optional":
		f |= FlagBlock | FlagOptional
	case "enum":
		f |= FlagEnum
	case "enum,optional":
		f |= FlagEnum | FlagOptional
	case "label":
		f |= FlagLabel
	case "squash":
		f |= FlagSquash
	default:
		return
	}
	return f, true
}

func printPathToField(structTy reflect.Type, path []int) string {
	var sb strings.Builder
	sb.WriteString(structTy.String())
	sb.WriteString(".")
	cur := structTy
	for i, elem := range path {
		sb.WriteString(cur.Field(elem).Name)
		if i+1 < len(path) {
			sb.WriteString(".")
		}
		cur = cur.Field(i).Type
	}
	return sb.String()
}

func deferenceType(ty reflect.Type) reflect.Type {
	for ty.Kind() == reflect.Pointer {
		ty = ty.Elem()
	}
	return ty
}

func isStructType(ty reflect.Type) bool {
	return ty.Kind() == reflect.Struct
}

func validateEnum(field reflect.StructField) error {
	kind := field.Type.Kind()
	if kind != reflect.Slice && kind != reflect.Array {
		return fmt.Errorf("enum fields can only be slices or arrays")
	}
	elementType := deferenceType(field.Type.Elem())
	if elementType.Kind() != reflect.Struct {
		return fmt.Errorf("enum fields can only be a slice or array of structs")
	}
	for _, f := range Get(elementType) {
		if !f.IsBlock() {
			return fmt.Errorf("fields in an enum element may only be blocks, got %s", f.Flags.String())
		}
		fieldType := deferenceType(elementType.FieldByIndex(f.Index).Type)
		if fieldType.Kind() != reflect.Struct {
			return fmt.Errorf("blocks in an enum element may only be structs, got %s", fieldType.Kind().String())
		}
	}
	return nil
}
