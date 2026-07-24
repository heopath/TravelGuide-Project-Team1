<img src="https://capsule-render.vercel.app/api?type=waving&color=0:4FACFE,100:00C9A7&theme=navy&height=250&section=header&text=All%20My%20Trips&fontSize=55&animation=fadeIn&desc=All%20My%20Trips%20Web%20Application%20Development&descSize=20&descAlignY=65" width="100%" />

<div align="center">

# ✈️ All My Trips

### 🌍 AI-Powered Travel Guide & Planner

**AI가 추천하고, 사용자가 완성하는 맞춤형 여행 플래너**

<br>

<img src="https://img.shields.io/badge/Project-All%20My%20Trips-4FACFE?style=for-the-badge" />
<img src="https://img.shields.io/badge/Status-Planning-00C9A7?style=for-the-badge" />
<img src="https://img.shields.io/badge/AI-Travel%20Planner-8A2BE2?style=for-the-badge" />

<br><br>

<img src="https://img.shields.io/badge/Java-21-007396?style=flat-square&logo=openjdk&logoColor=white" />
<img src="https://img.shields.io/badge/Spring%20Boot-6DB33F?style=flat-square&logo=springboot&logoColor=white" />
<img src="https://img.shields.io/badge/MySQL-4479A1?style=flat-square&logo=mysql&logoColor=white" />
<img src="https://img.shields.io/badge/Redis-DC382D?style=flat-square&logo=redis&logoColor=white" />
<img src="https://img.shields.io/badge/AWS-FF9900?style=flat-square&logo=amazonaws&logoColor=white" />

</div>

---

## 🌍 About All My Trips

All My Trips은 사용자의 **여행 기간, 목적지, 동행자, 여행 스타일, 예산**을 기반으로
AI가 맞춤형 여행 일정을 추천하고, 사용자가 직접 수정하여 자신만의 여행 계획을 완성할 수 있는 여행 플랫폼입니다.

단순한 여행지 추천을 넘어 다음과 같은 흐름으로 서비스를 확장하는 것을 목표로 합니다.

```text
AI 여행 추천
      ↓
사용자 일정 편집
      ↓
실시간 여행 정보
      ↓
숙박 · 교통 · 티켓 예약
      ↓
대규모 트래픽 처리
      ↓
RAG 기반 개인화 AI 여행 가이드
```

---

## ✈️ How All My Trips Works

| Step | Journey             |
| :--: | ------------------- |
| `01` | 📍 여행 조건 입력         |
| `02` | 🤖 AI 맞춤 여행 일정 생성   |
| `03` | 🗓️ 사용자 일정 수정 및 저장  |
| `04` | 🌤️ 실시간 여행 정보 확인    |
| `05` | 🎫 숙박 · 교통 · 티켓 예약  |
| `06` | 🧠 RAG 기반 개인화 여행 추천 |

---

<details>
<summary><b>📖 1. 프로젝트 소개</b></summary>

<br>

## 📝 프로젝트 개요

여행을 준비할 때 사용자는 관광지, 맛집, 숙박, 날씨, 교통 등
여러 정보를 각각 다른 서비스에서 찾아야 합니다.

All My Trips은 이러한 여행 준비 과정을 하나의 서비스로 통합하고,
사용자가 몇 가지 조건만 입력하면 AI가 맞춤형 여행 계획을 제안하도록 설계합니다.

AI가 만든 일정은 사용자가 자유롭게 수정할 수 있으며,
향후 실시간 정보와 예약 기능까지 연동하여 실제 여행 준비 과정 전체를 관리할 수 있도록 확장합니다.

---

## 🎯 프로젝트 핵심 목표

* AI 기반 맞춤형 여행 일정 생성
* 사용자 직접 여행 일정 편집
* 여행지 및 관광 정보 탐색
* 실시간 날씨와 지도 정보 제공
* 여행 일정 저장 및 관리
* 예약 시스템과 대기열 기능 구현
* 대규모 트래픽 및 동시성 문제 해결
* RAG 기반 개인화 AI 여행 가이드 구현

---

## 💡 Service Concept

```text
"어디로 갈까?"
      ↓
"무엇을 할까?"
      ↓
"어떤 일정으로 움직일까?"
      ↓
"어디에서 머물까?"
      ↓
"내 취향에 맞게 수정"
      ↓
"나만의 여행 계획 완성"
```

> All My Trips은 여행 계획의 시작부터 실제 여행 준비까지
> 하나의 서비스 안에서 해결하는 것을 목표로 합니다.

</details>

---

<details>
<summary><b>✨ 2. 핵심 기능</b></summary>

<br>

## 🤖 AI 여행 일정 추천

사용자가 입력한 여행 조건을 기반으로 AI가 맞춤형 여행 일정을 생성합니다.

### 입력 조건

* 📍 여행 지역
* 📅 여행 기간
* 👥 동행자
* 🎯 여행 목적
* ❤️ 여행 스타일
* 💰 예상 예산

### 예시

```text
여행 지역 : 제주도
여행 기간 : 3박 4일
동행자 : 친구
여행 스타일 : 맛집 + 관광
예산 : 1인 50만원
```

### AI 추천 결과

```text
DAY 1
제주공항
→ 동문시장
→ 용두암
→ 숙소 체크인

DAY 2
성산일출봉
→ 섭지코지
→ 우도

DAY 3
협재해수욕장
→ 오설록
→ 애월 카페거리

DAY 4
기념품 쇼핑
→ 제주공항
```

---

## 🗓️ 여행 일정 직접 편집

AI가 만든 일정을 그대로 사용하는 것이 아니라
사용자가 직접 여행 계획을 수정하고 완성할 수 있습니다.

* 일정 추가 / 삭제
* 장소 순서 변경
* 관광지 추가
* 맛집 추가
* 숙소 추가
* 메모 작성
* 일정 저장

---

## 📍 여행 장소 탐색

다양한 여행 정보를 검색하고 일정에 바로 추가할 수 있습니다.

* 관광지
* 맛집
* 카페
* 숙박
* 축제
* 체험 활동

---

## ⭐ 즐겨찾기

관심 있는 장소를 저장해두고
추후 여행 일정에 간편하게 추가할 수 있습니다.

---

## 🌤️ 실시간 여행 정보

외부 API를 활용하여 여행에 필요한 실시간 정보를 제공합니다.

* 현재 날씨
* 여행 기간 날씨 예보
* 관광 정보
* 지도 및 위치 정보
* 교통 정보

---

## 👤 회원 기능

* 회원가입
* 로그인
* 로그아웃
* 마이페이지
* 내 여행 일정 조회
* 즐겨찾기 관리

</details>

---

<details>
<summary><b>🗺️ 3. 서비스 플로우</b></summary>

<br>

## User Flow

```text
사용자 접속
      ↓
여행 조건 입력
      ↓
AI 여행 일정 생성
      ↓
일정 수정 및 저장
      ↓
날씨 / 지도 / 관광 정보 확인
      ↓
숙박 / 교통 / 티켓 조회
      ↓
여행 일정 최종 확정
```

---

## Main Page Concept

All My Trips의 첫 화면은
Google 검색 페이지처럼 복잡하지 않고 직관적인 UI를 목표로 합니다.

사용자가 사이트에 접속한 후 별도의 복잡한 탐색 없이
바로 여행 조건을 입력하고 AI 여행 계획을 생성할 수 있도록 구성합니다.

```text
              ✈️ All My Trips

       어디로 여행을 떠나시나요?

           [ 여행 지역 입력 ]

       얼마 동안 여행하시나요?

           [ 여행 기간 선택 ]

       누구와 함께 가시나요?

 [ 혼자 ] [ 친구 ] [ 연인 ] [ 가족 ]

       어떤 여행을 원하시나요?

 [ 관광 ] [ 맛집 ] [ 힐링 ] [ 액티비티 ]

         [ ✨ AI 여행 계획 만들기 ]
```

### 추가 설정

기본 화면을 복잡하게 만들지 않기 위해
필요한 사용자만 추가 옵션을 펼쳐 설정할 수 있도록 구성합니다.

* 예상 예산
* 이동 수단
* 선호 음식
* 여행 강도
* 숙박 스타일
* 관심 여행 테마

</details>

---

## 🚀 Development Roadmap

| Phase | 목표               | 핵심 기능                           |
| :---: | ---------------- | ------------------------------- |
|  `1차` | 🤖 AI 여행 플래너 MVP | AI 일정 생성, 일정 관리, 즐겨찾기           |
|  `2차` | ⚡ 실서비스 기능 확장     | 실시간 API, 예약, Redis, 대기열, 부하 테스트 |
|  `3차` | 🧠 AI 개인화 고도화    | RAG, Vector DB, 일정 분석, 동선 최적화   |

---

<details>
<summary><b>1️⃣ 4-1. 1차 구현 - MVP</b></summary>

<br>

## AI 여행 가이드 & 여행 일정 관리

All My Trips의 핵심 서비스를 먼저 구현하는 단계입니다.

### 주요 기능

* 메인 화면
* AI 여행 일정 생성
* 일정 생성 / 조회 / 수정 / 삭제
* 사용자 직접 일정 작성
* 여행지 탐색
* 즐겨찾기
* 회원 기능
* 마이페이지

### 핵심 목표

> **AI가 추천하고, 사용자가 직접 완성하는 여행 플래너**

1차 구현에서는 All My Trips 서비스의 핵심 가치인
**AI 추천 + 사용자 일정 관리** 기능을 완성하는 것을 목표로 합니다.

</details>

---

<details>
<summary><b>2️⃣ 4-2. 2차 구현 - 서비스 확장</b></summary>

<br>

## 실시간 여행 정보 & 예약 시스템

1차 구현에서 만든 여행 일정 서비스를
실제 여행 서비스에 가까운 형태로 확장합니다.

### 🌤️ 실시간 API

* 날씨 API
* 지도 API
* 관광 정보 API
* 위치 기반 검색
* 교통 정보 API

---

### 🏨 숙박 및 교통 정보

* 지역별 숙소 검색
* 숙소 상세 정보
* 숙소 위치 확인
* 항공편 정보
* 기차 / 버스 정보
* 이동 경로 확인

---

### 🎫 관광지 티켓 예약

관광지, 축제, 체험 상품 등의 티켓을 예약할 수 있는 기능을 구현합니다.

```text
제주 아쿠아플라넷

예약 가능 수량 : 100

[ 예약하기 ]
```

---

## ⚡ 대규모 트래픽 테스트

한정 수량의 티켓에 많은 사용자가 동시에 접근하는 상황을 구현합니다.

```text
한정 수량 관광 티켓

티켓 수량 : 100개
동시 요청 사용자 : 10,000명
```

### 해결하고자 하는 문제

* 동시 예약 요청
* 중복 예약
* 재고 초과 판매
* DB Lock 문제
* 서버 과부하

---

## 🔒 동시성 제어

검토 기술

```text
Redis
Distributed Lock
Optimistic Lock
Pessimistic Lock
```

---

## 🚦 대기열 시스템

대규모 사용자가 동시에 예약 페이지에 접근하는 경우
서버 부하를 줄이기 위한 대기열 시스템을 구현합니다.

```text
현재 접속자가 많습니다.

현재 대기 순번
324명

예상 대기 시간
약 2분
```

검토 기술

```text
Redis
Queue
Kafka
WebSocket
SSE
```

---

## 📊 부하 테스트

검토 도구

```text
JMeter
k6
nGrinder
```

### 핵심 목표

> **실제 서비스 환경에서 발생할 수 있는 트래픽과 동시성 문제를 직접 재현하고 해결**

</details>

---

<details>
<summary><b>3️⃣ 4-3. 3차 구현 - AI 고도화</b></summary>

<br>

## RAG 기반 AI 여행 가이드

AI 기능을 단순한 LLM 응답에서
실제 여행 데이터를 기반으로 답변할 수 있는 구조로 발전시킵니다.

---

## 🧠 RAG 기반 여행 AI

사용자 질문과 관련된 여행 데이터를 먼저 검색한 후
해당 데이터를 기반으로 LLM이 답변을 생성합니다.

```text
사용자 질문
      ↓
Embedding
      ↓
Vector Database 검색
      ↓
관련 여행 데이터 추출
      ↓
LLM
      ↓
맞춤형 여행 답변
```

---

## 🗄️ Vector Database

활용 데이터

* 관광지 정보
* 맛집 정보
* 여행 후기
* 지역별 여행 가이드
* 여행 일정 데이터

검토 기술

```text
FAISS
ChromaDB
Pinecone
Milvus
```

---

## 💬 AI 여행 챗봇

```text
"서울에서 비 오는 날 갈만한 곳 추천해줘"

"제주도 3박 4일 가족 여행 일정 만들어줘"

"부산에서 회 말고 먹을 만한 음식 추천해줘"

"현재 여행 일정에서 이동 동선을 줄여줘"

"오늘 날씨에 맞게 일정을 변경해줘"
```

---

## 🔄 AI 여행 일정 최적화

사용자가 만든 여행 일정의 위치와 이동 거리를 분석하여
더 효율적인 여행 동선을 추천합니다.

### 기존 일정

```text
09:00 성산일출봉
11:00 협재해수욕장
13:00 우도
```

### AI 분석

```text
현재 일정은 장소 간 이동 거리가 길어
효율적이지 않을 수 있습니다.

성산일출봉
→ 우도
→ 섭지코지

순서로 변경하는 것을 추천합니다.
```

---

## 👤 사용자 맞춤형 추천

사용자의 기존 여행 기록과 선호도를 분석하여
향후 여행 일정 생성 시 개인화된 추천을 제공합니다.

```text
사용자 여행 선호도

맛집      40%
카페      30%
관광      20%
액티비티  10%
```

### 핵심 목표

> **사용자의 여행 데이터를 이해하고 개인화된 추천을 제공하는 AI Travel Assistant 구현**

</details>

---

<details>
<summary><b>🛠️ 5. 기술 스택</b></summary>

<br>

## Backend

<img src="https://img.shields.io/badge/Java%2017-007396?style=for-the-badge&logo=openjdk&logoColor=white">
<img src="https://img.shields.io/badge/Spring%20Boot-6DB33F?style=for-the-badge&logo=springboot&logoColor=white">
<img src="https://img.shields.io/badge/Spring%20Security-6DB33F?style=for-the-badge&logo=springsecurity&logoColor=white">
<img src="https://img.shields.io/badge/JPA-59666C?style=for-the-badge">
<img src="https://img.shields.io/badge/MyBatis-000000?style=for-the-badge">

---

## Frontend

<img src="https://img.shields.io/badge/HTML5-E34F26?style=for-the-badge&logo=html5&logoColor=white">
<img src="https://img.shields.io/badge/CSS3-1572B6?style=for-the-badge&logo=css3&logoColor=white">
<img src="https://img.shields.io/badge/JavaScript-F7DF1E?style=for-the-badge&logo=javascript&logoColor=black">
<img src="https://img.shields.io/badge/Thymeleaf-005F0F?style=for-the-badge&logo=thymeleaf&logoColor=white">

---

## Database / Cache

<img src="https://img.shields.io/badge/MySQL-4479A1?style=for-the-badge&logo=mysql&logoColor=white">
<img src="https://img.shields.io/badge/Redis-DC382D?style=for-the-badge&logo=redis&logoColor=white">

---

## AI

<img src="https://img.shields.io/badge/Ollama-000000?style=for-the-badge">
<img src="https://img.shields.io/badge/Gemini%20API-4285F4?style=for-the-badge&logo=google&logoColor=white">
<img src="https://img.shields.io/badge/RAG-8A2BE2?style=for-the-badge">
<img src="https://img.shields.io/badge/Vector%20DB-4B0082?style=for-the-badge">

---

## Infrastructure

<img src="https://img.shields.io/badge/AWS%20EC2-FF9900?style=for-the-badge&logo=amazonec2&logoColor=white">
<img src="https://img.shields.io/badge/Nginx-009639?style=for-the-badge&logo=nginx&logoColor=white">
<img src="https://img.shields.io/badge/Docker-2496ED?style=for-the-badge&logo=docker&logoColor=white">
<img src="https://img.shields.io/badge/GitHub%20Actions-2088FF?style=for-the-badge&logo=githubactions&logoColor=white">

---

## Collaboration

<img src="https://img.shields.io/badge/Git-F05032?style=for-the-badge&logo=git&logoColor=white">
<img src="https://img.shields.io/badge/GitHub-181717?style=for-the-badge&logo=github&logoColor=white">
<img src="https://img.shields.io/badge/Figma-F24E1E?style=for-the-badge&logo=figma&logoColor=white">

</details>

---

<details>
<summary><b>⚙️ 6. 시스템 아키텍처</b></summary>

<br>

```text
                 ┌──────────────┐
                 │    Client    │
                 │  Web Browser │
                 └──────┬───────┘
                        │
                        ▼
                 ┌──────────────┐
                 │    Nginx     │
                 └──────┬───────┘
                        │
                        ▼
               ┌────────────────┐
               │  Spring Boot   │
               │  Application   │
               └───────┬────────┘
                       │
          ┌────────────┼────────────┐
          │            │            │
          ▼            ▼            ▼
       MySQL         Redis       AI Service
                                      │
                           ┌──────────┴──────────┐
                           │                     │
                           ▼                     ▼
                          LLM              Vector Database
```

</details>

---

<details>
<summary><b>🗄️ 7. 데이터베이스 설계</b></summary>

<br>

<details>
<summary><b>📊 7-1. 테이블 생성 SQL (DDL)</b></summary>

<br>

[📄 All My Trips 통합 DDL 보기](docs/database/all_my_trips_schema.sql)

[🧩 V1~V7 마이그레이션·로컬 seed·검증 실행 안내](database/README.md)

</details>

<br>

<details>
<summary><b>📋 7-2. DB 테이블 구조 및 컬럼 설명</b></summary>

<br>

[📐 ERD 초안 및 테이블·컬럼 설계서 보기](docs/database/all_my_trips_database.md)

> 현재 문서는 서비스 설계 과정에서 변경될 수 있는 초안이며, 변경 이력은 Git으로 관리합니다.

</details>

</details>

---

<details>
<summary><b>📂 8. 프로젝트 구조</b></summary>

<br>

```text
AllMyTrips/
│
├── backend/
│   ├── controller/
│   ├── service/
│   ├── repository/
│   ├── domain/
│   └── config/
│
├── frontend/
│
├── ai/
│   ├── llm/
│   ├── rag/
│   └── embedding/
│
├── database/
│   ├── init/
│   ├── migration/
│   ├── seed/
│   ├── validation/
│   └── README.md
│
├── docs/
│   ├── database/
│   ├── planning/
│   ├── screen-design/
│   ├── flowchart/
│   └── erd/
│
└── README.md
```

</details>

---

<details>
<summary><b>👥 9. 팀원 구성</b></summary>

<br>

|    역할    |    이름   |
| :------: | :-----: |
|   👑 팀장  | **허민재** |
| 👨‍💻 팀원 |   정인길   |
| 👨‍💻 팀원 |   홍유원   |
| 👨‍💻 팀원 |   남현호   |
| 👨‍💻 팀원 |   한성주   |

[📘 백엔드 도메인·Service 역할 분담 초안](All_My_Trip_Project/docs/backend-service-role-plan.md)

</details>

---

<details>
<summary><b>📈 10. 프로젝트를 통해 얻고자 하는 경험</b></summary>

<br>

## 🌐 Web Development

* Spring Boot 기반 웹 서비스 설계
* REST API 설계
* 사용자 인증 및 권한 관리
* DB 모델링
* 외부 API 연동

## 🤖 AI

* AI API 연동
* Local LLM 활용
* RAG 구조 설계
* Embedding 활용
* Vector Database 활용

## ⚡ Performance

* Redis 활용
* 예약 동시성 문제 해결
* 대규모 트래픽 부하 테스트
* 대기열 시스템 설계
* 서비스 성능 개선

## ☁️ Infrastructure

* AWS 서버 배포
* Nginx 구성
* Docker 활용
* GitHub Actions 기반 CI/CD

</details>

---

<details>
<summary><b>🔮 11. 향후 확장 기능</b></summary>

<br>

* 실시간 날씨 기반 일정 변경
* 지도 기반 여행 동선 최적화
* 항공 및 숙박 정보 연동
* 관광지 티켓 예약
* Redis 기반 예약 동시성 제어
* 대규모 트래픽 대기열 시스템
* 사용자 여행 패턴 분석
* 개인 맞춤형 여행 추천
* RAG 기반 여행 챗봇

</details>

---

## 🔗 Project Links

|     구분    | 링크    |
| :-------: | ----- |
|  🎨 Figma | [바로가기](https://www.figma.com/design/byqjrBMhrQzNsuE7AWJCfd/All-My-Trips-%E2%80%94-AI-%EB%A7%9E%EC%B6%A4-%EC%97%AC%ED%96%89-%ED%94%8C%EB%9E%AB%ED%8F%BC-%EC%A0%84%EC%B2%B4-%ED%99%94%EB%A9%B4--%ED%97%88%EB%AF%BC%EC%9E%AC-%ED%8C%80-?node-id=3-4310&p=f&t=FA8LwNIAQxCFGwsn-0) |
| 📖 Notion | [바로가기](https://app.notion.com/p/3-624346ade5b782f99b0201fe8ba60557) |
|  🌐 Demo  | 추후 추가 |
|  🎬 Video | 추후 추가 |

---

<div align="center">

### ✈️ All My Trips

**Plan Smarter. Travel Better.**

</div>



<img src="https://capsule-render.vercel.app/api?type=waving&color=0:4FACFE,100:00C9A7&height=160&section=footer&text=Ready%20for%20Your%20Next%20Journey&fontSize=26&animation=fadeIn" width="100%" />
