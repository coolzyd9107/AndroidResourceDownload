// Package validator wires the gin request validator.
package validator

import (
	"github.com/gin-gonic/gin/binding"
	"github.com/go-playground/validator/v10"
)

// Init registers any custom validators. Currently a placeholder; kept for
// future extensions (e.g. validate email-domain tag).
func Init() error {
	v, ok := binding.Validator.Engine().(*validator.Validate)
	if !ok {
		return nil
	}
	_ = v
	return nil
}
