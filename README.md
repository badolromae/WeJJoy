# WeJJoy — 함께 쓰는 다이어리 (안드로이드)

**EnJJoy 다이어리의 모든 기능과 디자인을 그대로** 가져오고,
여기에 **Firebase 실시간 공유** 를 더한 그룹 다이어리 앱입니다.

## 이 앱이 하는 일
- **관리자(메인) / 공유자(서브)** 구조로, 같은 그룹끼리 다이어리가 **자동으로 공유** 됩니다.
- 한 명이 일기를 쓰거나 고치면 **상대방 앱에 실시간으로 자동 반영** 됩니다. (새로고침 불필요)
- 연결은 **6자리 초대 코드**로 합니다. 관리자가 코드를 알려주고, 공유자가 코드 + 별명으로 참여합니다.
- **설정창의 공유자 목록**에서 관리자가 공유자를 **승인 / 해제** 할 수 있습니다.
- 디자인은 **8종**(딥그린·스카이블루·연핑크·블랙+그레이·라벤더 퍼플·선셋 오렌지·다크 그린·미드나잇 블루, 기존 4종은 밝은/어두운 모드 자동 지원).
  각자 기기에서 **디자인은 달라도 되고, 다이어리 내용은 모두 같습니다.**

## 기존 EnJJoy 기능 (모두 동일)
- 일기·일정: 시작~종료(여러 날 가능), 제목·본문, 기분 이모지, 중요도(1~100%), 태그, 사진 첨부
- 한국 공휴일·대체공휴일·기념일, 음력 표시
- 홈 화면 위젯 3종 (월 / 주 / 일) — 위젯도 선택한 디자인으로 함께 바뀜
- 매일 알림 + 개별 일정 알림 (소리/진동/무음 설정)
- PIN + 지문/얼굴 잠금
- 검색

---

## 1. Firebase 설정 (공유 기능에 꼭 필요, 1회만)

1. https://console.firebase.google.com 접속 → **프로젝트 추가** (이름은 아무거나, 예: wejjoy)
2. 프로젝트 만들어지면 왼쪽 **빌드(Build) ▸ Authentication ▸ 시작하기** → **익명(Anonymous)** 로그인 **사용 설정**
3. **빌드 ▸ Firestore Database ▸ 데이터베이스 만들기** → 위치 `asia-northeast3 (서울)` → **테스트 모드로 시작**
   - 만들어진 뒤 **규칙(Rules)** 탭에서 아래로 바꾸고 **게시**:
     ```
     rules_version = '2';
     service cloud.firestore {
       match /databases/{database}/documents {
         match /groups/{groupId} {
           allow read, write: if request.auth != null;
           match /entries/{entryId} {
             allow read, write: if request.auth != null;
           }
         }
       }
     }
     ```
   - (테스트 모드 규칙은 30일 뒤 만료될 수 있으니 위 규칙으로 꼭 바꿔 주세요)
4. **빌드 ▸ Storage ▸ 시작하기** → **테스트 모드** → 완료 후 규칙을 아래로 변경:
     ```
     rules_version = '2';
     service firebase.storage {
       match /b/{bucket}/o {
         match /groups/{groupId}/{allPaths=**} {
           allow read, write: if request.auth != null;
         }
       }
     }
     ```
5. 프로젝트 개요(톱니바퀴 ⚙ 옆) → **앱 추가 ▸ Android**
   - 패키지 이름: **`com.wejjoy.diary`** (반드시 정확히)
   - 닉네임: WeJJoy → **앱 등록**
6. **`google-services.json` 다운로드** → 이 프로젝트의 **`app/` 폴더 안에** 넣기
   - 즉 `app/google-services.json` 위치
   - 이 파일에는 내 Firebase 비밀키가 들어 있으므로, 공개 저장소에는 올리지 않는 것이 좋습니다.
     (이 저장소의 `.gitignore`에 이미 제외되어 있습니다)

> `google-services.json` 없이 빌드하면 공유 기능만 꺼지고, 나머지는 일반 다이어리로 동작합니다.

---

## 2. APK 빌드 (GitHub 자동 빌드 — 컴퓨터에 아무것도 설치 불필요)

1. https://github.com/new 에서 저장소 `diary-group` (비공개 추천) 생성
2. 이 프로젝트의 **모든 파일·폴더를** (숨김 폴더 `.github` 포함) 업로드
3. **위 1-6에서 받은 `google-services.json` 은 절대 깃허브에 올리지 마세요.**
   대신 아래 방법으로 비밀 등록합니다:
   - `google-services.json` 파일을 메모장으로 열어 **전체 내용을 복사**
   - 저장소 **Settings ▸ Secrets and variables ▸ Actions ▸ New repository secret**
   - Name: `GOOGLE_SERVICES_JSON` / Secret: 복사한 내용 전체 붙여넣기 → **Add secret**
   - (빌드 시 GitHub이 이 비밀을 `app/google-services.json` 으로 자동 생성합니다)
4. **Actions** 탭 → `Build APK` 가 자동 실행(또는 Run workflow) → 초록 ✓ 되면
   맨 아래 **Artifacts ▸ WeJJoy-APK** 다운로드 → `app-debug.apk` 를 폰에 설치

> 그냥 테스트만 하려면 비밀 등록 없이 빌드해도 됩니다. 그 경우 공유 기능은 꺼진 채로 설치됩니다.

## 3. 사용법 (두 명이 연결하기)

**관리자 (메인 앱)**
1. 설정 → **공유 다이어리** → 내 별명 입력 → **그룹 만들기 (관리자)**
2. 화면에 표시되는 **초대 코드 6자리**를 상대에게 전달 (누르면 복사됨)

**공유자 (서브 앱)**
1. 설정 → **공유 다이어리** → 내 별명 + 받은 **초대 코드 6자리** 입력 → **코드로 참여**
2. "관리자 승인 대기 중" 표시

**관리자**
3. 설정 → 공유자 목록에 상대가 "승인 대기"로 뜨면 **승인** 버튼
4. 이 순간부터 **양쪽 앱의 일기가 실시간으로 같아집니다.**
5. 공유를 끊고 싶으면 목록에서 **해제** — 상대 기기에서는 즉시 공유가 중단됩니다.

- 인원 제한 없이 여러 명을 같은 코드로 초대할 수 있습니다.
- 알림(매일/개별)은 **각자 기기에서만** 울리고 상대에게 전송되지 않습니다.

## 기술 정보
- Kotlin / minSdk 26 / targetSdk 34 / Room(로컬 DB) + Firebase Auth·Firestore·Storage(공유)
- 패키지명: `com.wejjoy.diary` (EnJJoy 와 별도 앱으로 함께 설치 가능)
- 로컬 우선: 오프라인에서도 일기 작성 가능, 온라인이 되면 자동 동기화
