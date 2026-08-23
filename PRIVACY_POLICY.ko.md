# 개인정보처리방침

**최종 수정일: 2026년 8월 23일**

> This document is a Korean translation of [PRIVACY_POLICY.md](PRIVACY_POLICY.md). 내용이 다를 경우 영어 원문이 우선합니다.

## 개요

ClaudeMeter("앱")는 Claude.ai(및 선택적으로 ChatGPT/Codex) 사용량 정보를 실시간으로 보여주는 오픈소스 Android 유틸리티입니다. 본 방침은 앱이 사용자의 데이터를 어떻게 처리하는지 설명합니다.

## 데이터 수집

앱 자체는 개발자가 운영하는 서버로 어떠한 개인 데이터도 수집·저장·전송하지 **않습니다**. 다만 앱에는 Google AdMob 광고 SDK가 포함되어 있으며, 아래 [광고](#광고-google-admob) 섹션에 설명된 식별자를 수집합니다.

### 앱이 기기에 로컬로 저장하는 정보:
- **Claude 세션 키**: Claude.ai API 인증에 사용됩니다. 암호화 저장소(Android EncryptedSharedPreferences)에 기기 내에만 저장됩니다.
- **ChatGPT 자격증명** (선택): Codex 사용량 추적을 연결한 경우, 액세스 토큰과 세션 쿠키가 기기 내 암호화 저장소에만 저장됩니다.
- **조직 ID**: 사용량 데이터를 가져오는 데 사용되며 로컬에 저장됩니다.
- **표시 설정**: 지표 표시 여부 및 알림 설정.
- **사용량 히스토리**: 예측 기능을 위해 최근 사용량 스냅샷을 로컬에 보관합니다.

### 앱이 하지 않는 것:
- 분석(analytics)이나 원격 측정(telemetry) 데이터를 수집하지 않습니다
- Claude/ChatGPT 자격증명이나 사용량 데이터를 제3자와 공유하지 않습니다
- 개발자가 운영하는 어떤 서버로도 데이터를 보내지 않습니다

## 광고 (Google AdMob)

앱은 **Google AdMob**이 제공하는 광고(배너 및 전면 광고)를 표시합니다. 광고 게재 및 측정을 위해 Google Mobile Ads SDK가 다음 정보를 자동으로 수집하여 Google과 공유할 수 있습니다:

- **기기 또는 기타 ID** — Android **광고 ID(Advertising ID)**
- **IP 주소** 및 이로부터 파생된 대략적인 위치
- **광고 상호작용 데이터**(노출, 클릭) 및 진단 정보

이 데이터는 개발자가 아닌 Google이 수집하며, 광고 게재, 광고 측정, 부정행위 방지에 사용됩니다. 자세한 내용은 다음을 참고하세요:

- [Google 서비스를 사용하는 사이트 또는 앱에서 Google이 정보를 사용하는 방법](https://policies.google.com/technologies/partner-sites?hl=ko)
- [Google 개인정보처리방침](https://policies.google.com/privacy?hl=ko)

기기 설정의 **설정 → Google → 광고**(또는 **설정 → 개인정보 보호 → 광고**)에서 언제든지 광고 개인 최적화를 제한하거나 광고 ID를 삭제/재설정할 수 있습니다.

## 네트워크 요청

앱은 다음 서버와 통신합니다:

- **`claude.ai`** — 사용량 지표, 지출 한도 정보, 선불 잔액 조회 (예: `/api/organizations/{id}/usage`)
- **`chatgpt.com`** (Codex 추적을 활성화한 경우에만) — Codex 사용량 인증 및 조회
- **Google 광고 서버** — AdMob 광고 로드 및 표시

그 외의 네트워크 요청은 없습니다.

## 데이터 보안

- 세션 키와 토큰은 기기 내 암호화 저장소에만 저장됩니다
- 모든 네트워크 통신은 HTTPS를 사용합니다

## 제3자 서비스

- 광고 표시를 위해 위에 설명한 대로 **Google AdMob**을 사용합니다.
- 이 앱은 어떤 방식으로도 **Anthropic 또는 OpenAI와 제휴하거나, 승인받거나, 공식적으로 연결되어 있지 않습니다**. Claude.ai 및 ChatGPT 웹 인터페이스가 사용하는 것과 동일한 API 엔드포인트를 사용합니다.

## 아동의 개인정보

이 앱은 만 13세 미만 아동의 사용을 대상으로 하지 않습니다.

## 변경 사항

본 방침은 수시로 업데이트될 수 있으며, 변경 사항은 이 저장소에 게시됩니다.

## 문의

본 방침에 대해 궁금한 점이 있으면 [GitHub](https://github.com/CUN-bjy/claude-meter)에 이슈를 등록해 주세요.

---
