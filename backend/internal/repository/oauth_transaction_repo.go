package repository

import (
	"errors"
	"time"

	"gorm.io/gorm"

	"link.mczihan/webdavbox-backend/internal/model"
)

const (
	OAuthPending    = "PENDING"
	OAuthProcessing = "PROCESSING"
	OAuthReady      = "READY"
	OAuthConsumed   = "CONSUMED"
)

type OAuthTransactionRepo struct{ db *gorm.DB }

func (r *OAuthTransactionRepo) Create(transaction *model.OAuthTransaction) error {
	return r.db.Create(transaction).Error
}

func (r *OAuthTransactionRepo) ClaimCallback(stateHash string, now time.Time) (*model.OAuthTransaction, error) {
	var transaction model.OAuthTransaction
	err := r.db.Transaction(func(tx *gorm.DB) error {
		if err := tx.First(&transaction, "state_hash = ? AND status = ? AND state_expires_at > ?", stateHash, OAuthPending, now).Error; err != nil {
			return err
		}
		result := tx.Model(&model.OAuthTransaction{}).
			Where("id = ? AND status = ?", transaction.ID, OAuthPending).
			Updates(map[string]any{"status": OAuthProcessing, "updated_at": now})
		if result.Error != nil {
			return result.Error
		}
		if result.RowsAffected != 1 {
			return gorm.ErrRecordNotFound
		}
		transaction.Status = OAuthProcessing
		return nil
	})
	if errors.Is(err, gorm.ErrRecordNotFound) {
		return nil, nil
	}
	return &transaction, err
}

func (r *OAuthTransactionRepo) AppStateForState(stateHash string, now time.Time) (string, bool, error) {
	var transaction model.OAuthTransaction
	err := r.db.First(&transaction, "state_hash = ? AND state_expires_at > ?", stateHash, now).Error
	if errors.Is(err, gorm.ErrRecordNotFound) {
		return "", false, nil
	}
	if err != nil {
		return "", false, err
	}
	return transaction.AppState, true, nil
}

func (r *OAuthTransactionRepo) MarkReady(id, userID, codeHash string, codeExpiresAt, now time.Time) error {
	result := r.db.Model(&model.OAuthTransaction{}).
		Where("id = ? AND status = ?", id, OAuthProcessing).
		Updates(map[string]any{
			"status": OAuthReady, "user_id": userID, "completion_code_hash": codeHash,
			"code_expires_at": codeExpiresAt, "updated_at": now,
		})
	if result.Error != nil {
		return result.Error
	}
	if result.RowsAffected != 1 {
		return gorm.ErrRecordNotFound
	}
	return nil
}

func (r *OAuthTransactionRepo) Consume(codeHash, codeChallenge string, now time.Time) (*model.OAuthTransaction, error) {
	var transaction model.OAuthTransaction
	err := r.db.Transaction(func(tx *gorm.DB) error {
		if err := tx.First(
			&transaction,
			"completion_code_hash = ? AND code_challenge = ? AND status = ? AND code_expires_at > ?",
			codeHash, codeChallenge, OAuthReady, now,
		).Error; err != nil {
			return err
		}
		result := tx.Model(&model.OAuthTransaction{}).
			Where("id = ? AND status = ?", transaction.ID, OAuthReady).
			Updates(map[string]any{"status": OAuthConsumed, "consumed_at": now, "updated_at": now})
		if result.Error != nil {
			return result.Error
		}
		if result.RowsAffected != 1 {
			return gorm.ErrRecordNotFound
		}
		return nil
	})
	if errors.Is(err, gorm.ErrRecordNotFound) {
		return nil, nil
	}
	return &transaction, err
}
