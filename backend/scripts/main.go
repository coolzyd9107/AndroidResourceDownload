// Command seed-admin inserts (or updates) an entry in admin_github_users
// to grant ADMIN role to a specific GitHub account.
//
// Usage:
//
//	ID=1234567 LOGIN=alice NOTE="ops team" go run ./scripts
package main

import (
	"context"
	"flag"
	"fmt"
	"log/slog"
	"os"
	"strconv"
	"time"

	"github.com/google/uuid"

	"link.mczihan/webdavbox-backend/internal/config"
	"link.mczihan/webdavbox-backend/internal/model"
	"link.mczihan/webdavbox-backend/internal/repository"
)

func main() {
	idFlag := flag.Int64("id", 0, "GitHub numeric id")
	loginFlag := flag.String("login", "", "GitHub login")
	noteFlag := flag.String("note", "", "optional note")
	flag.Parse()

	ghID := *idFlag
	if ghID == 0 {
		if v := os.Getenv("ID"); v != "" {
			n, err := strconv.ParseInt(v, 10, 64)
			if err != nil {
				fatal("invalid ID env var: " + err.Error())
			}
			ghID = n
		}
	}
	if ghID == 0 {
		fatal("missing --id or ID env")
	}
	login := *loginFlag
	if login == "" {
		login = os.Getenv("LOGIN")
	}
	note := *noteFlag
	if note == "" {
		note = os.Getenv("NOTE")
	}

	cfg, err := config.Load(os.Getenv("CONFIG_FILE"))
	if err != nil {
		fatal("config: " + err.Error())
	}
	db, err := repository.Open(cfg)
	if err != nil {
		fatal("db open: " + err.Error())
	}
	if err := repository.Migrate(db); err != nil {
		fatal("migrate: " + err.Error())
	}
	repos := repository.New(db)

	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	var loginPtr *string
	if login != "" {
		loginPtr = &login
	}
	var notePtr *string
	if note != "" {
		notePtr = &note
	}

	if existing, _ := repos.AdminGithub.Get(ghID); existing != nil {
		existing.GithubLogin = loginPtr
		existing.Note = notePtr
		if err := db.Save(existing).Error; err != nil {
			fatal("save: " + err.Error())
		}
		slog.Info("updated admin entry", slog.Int64("github_id", ghID))
	} else {
		row := &model.AdminGithubUser{
			GithubID:   ghID,
			GithubLogin: loginPtr,
			Note:       notePtr,
			CreatedAt:  time.Now(),
		}
		_ = uuid.NewString // avoid unused import warning if we drop the column later
		if err := repos.AdminGithub.Create(row); err != nil {
			fatal("create: " + err.Error())
		}
		slog.Info("created admin entry", slog.Int64("github_id", ghID))
	}
	_ = ctx
}

func fatal(msg string) {
	fmt.Fprintln(os.Stderr, "seed-admin:", msg)
	os.Exit(1)
}
