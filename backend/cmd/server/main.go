// Command server is the WebDAVBox backend entry point.
package main

import (
	"context"
	"log/slog"
	"os"
	"os/signal"
	"strconv"
	"syscall"
	"time"

	"github.com/gin-gonic/gin"

	"link.mczihan/webdavbox-backend/internal/app"
	"link.mczihan/webdavbox-backend/internal/config"
	"link.mczihan/webdavbox-backend/internal/middleware"
	"link.mczihan/webdavbox-backend/internal/pkg/crypto"
	"link.mczihan/webdavbox-backend/internal/pkg/jwt"
	"link.mczihan/webdavbox-backend/internal/pkg/logger"
	"link.mczihan/webdavbox-backend/internal/pkg/validator"
	"link.mczihan/webdavbox-backend/internal/ratelimit"
	"link.mczihan/webdavbox-backend/internal/repository"
	"link.mczihan/webdavbox-backend/internal/service"
)

func main() {
	log := logger.Init(os.Getenv("APP_ENV"))

	cfg, err := config.Load(os.Getenv("CONFIG_FILE"))
	if err != nil {
		log.Error("config load failed", slog.String("err", err.Error()))
		os.Exit(1)
	}

	if err := validator.Init(); err != nil {
		log.Error("validator init failed", slog.String("err", err.Error()))
		os.Exit(1)
	}

	db, err := repository.Open(cfg)
	if err != nil {
		log.Error("db open failed", slog.String("err", err.Error()))
		os.Exit(1)
	}
	if err := repository.Migrate(db); err != nil {
		log.Error("db migrate failed", slog.String("err", err.Error()))
		os.Exit(1)
	}
	repos := repository.New(db)

	issuer, err := jwt.NewIssuer(cfg.JWT.Secret, cfg.JWT.Issuer)
	if err != nil {
		log.Error("jwt issuer failed", slog.String("err", err.Error()))
		os.Exit(1)
	}

	suite := crypto.NewSuite(cfg.Credential.Secret, cfg.Update.Secret)
	roles := service.NewRoleService(cfg.UserEmailDomains, cfg.AdminEmailDomains)
	tokens := service.NewTokenService(issuer, cfg.JWT.AccessTTLSeconds, cfg.JWT.RefreshTTLDays, repos.RefreshTokens)
	emails := service.NewEmailService(&cfg.Email, repos.EmailCodes, log)
	github := service.NewGitHubClient(&cfg.Github)
	credSvc := service.NewCredentialService(suite, service.NewWebDAVConfigAdapter(&cfg.WebDAV), repos.CredentialLogs)
	updateSvc := service.NewUpdateService(suite, repos.AppVersions, repos.UpdateURLLogs, cfg.Update.TTLSeconds)
	authSvc := service.NewAuthService(repos.Users, repos.Identities, repos.AdminGithub, tokens, roles, emails, github, repos.AuditLogs, log)
	githubOAuth := service.NewGithubOAuthService(authSvc, github, repos.OAuthTransactions, &cfg.Github)
	limiter := ratelimit.NewInMemory(ratelimit.DefaultRules()...)

	gin.SetMode(gin.ReleaseMode)
	r := gin.New()
	r.Use(middleware.TraceID())
	r.Use(middleware.Recovery(log))
	r.Use(middleware.RequestLogger(log))
	r.Use(middleware.CORS())

	app.RegisterRoutes(r, &app.Deps{
		Config:      cfg,
		Logger:      log,
		JWTIssuer:   issuer,
		Limiter:     limiter,
		Auth:        authSvc,
		Credentials: credSvc,
		Updates:     updateSvc,
		Tokens:      tokens,
		GithubOAuth: githubOAuth,
	})

	srv := &app.Server{
		Addr:    ":" + strconv.Itoa(cfg.Server.Port),
		Handler: r,
		Log:     log,
	}

	ctx, cancel := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer cancel()

	go func() {
		log.Info("server starting",
			slog.String("addr", srv.Addr),
			slog.String("driver", cfg.Database.Driver),
			slog.String("env", cfg.App.Env),
		)
		if err := srv.ListenAndServe(); err != nil {
			log.Info("server stopped", slog.String("err", err.Error()))
			cancel()
		}
	}()

	<-ctx.Done()
	log.Info("shutdown signal received")
	shutdownCtx, shutdownCancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer shutdownCancel()
	if err := srv.Shutdown(shutdownCtx); err != nil {
		log.Error("graceful shutdown failed", slog.String("err", err.Error()))
	}
	log.Info("bye")
}
