# android-release-toolkit

TAG ?=

## sync: Подтянуть тулкит (тег TAG, по умолчанию последний) в локальные приложения-потребители
sync:
	@./scripts/sync_consumers.sh "$(TAG)"

.PHONY: sync
