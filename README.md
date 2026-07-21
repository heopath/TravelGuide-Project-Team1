<img src="https://capsule-render.vercel.app/api?type=waving&color==0:4FACFE,100:00C9A7&theme=navy&height=250&section=header&text=TripGuide%20Project&fontSize=55&animation=fadeIn&desc=TripGuide%20Web%20Application%20Development&descSize=20&descAlignY=65" width="100%" />

# ✈️ TripPilot

### AI 기반 맞춤형 여행 가이드 & 여행 일정 플래닝 서비스

**AI가 추천하고, 사용자가 완성하는 여행 플래너**

<br>

<img src="https://img.shields.io/badge/Project-TripPilot-4FACFE?style=for-the-badge" />
<img src="https://img.shields.io/badge/Status-Planning-00C9A7?style=for-the-badge" />
<img src="https://img.shields.io/badge/AI-Travel%20Planner-8A2BE2?style=for-the-badge" />

<br><br>

<img src="https://img.shields.io/badge/Java-17-007396?style=flat-square&logo=openjdk&logoColor=white" />
<img src="https://img.shields.io/badge/Spring%20Boot-6DB33F?style=flat-square&logo=springboot&logoColor=white" />
<img src="https://img.shields.io/badge/MySQL-4479A1?style=flat-square&logo=mysql&logoColor=white" />
<img src="https://img.shields.io/badge/Redis-DC382D?style=flat-square&logo=redis&logoColor=white" />
<img src="https://img.shields.io/badge/AWS-FF9900?style=flat-square&logo=amazonaws&logoColor=white" />

</div>

---

## 🌍 About TripPilot

TripPilot은 사용자의 **여행 기간, 목적지, 동행자, 여행 스타일, 예산**을 기반으로  
AI가 맞춤형 여행 일정을 추천하고, 사용자가 직접 수정하여 자신만의 여행 계획을 완성할 수 있는 여행 플랫폼입니다.

단순한 여행지 추천을 넘어 아래와 같은 흐름으로 서비스를 확장하는 것을 목표로 합니다.

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
> ✈️ **Plan smarter, travel better with TripPilot.**

---

<details>
<summary><b>📖 1. 프로젝트 소개</b></summary>

<br>

## 📝 프로젝트 개요

여행을 준비할 때 사용자는 관광지, 맛집, 숙박, 날씨, 교통 등
여러 정보를 각각 다른 서비스에서 찾아야 합니다.

TripPilot은 이러한 여행 준비 과정을 하나의 서비스로 통합하고,
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
* RAG 기반 개인화 AI 여행 가이드 구현

---

## 💡 Service Concept

```text
"어디로 갈까?"
      ↓
"무엇을 할까?"
      ↓
"어떻게 이동할까?"
      ↓
"어디에서 머물까?"
      ↓
"내 일정에 맞게 수정"
```

TripPilot은 여행 계획의 시작부터 실제 여행 준비까지
하나의 서비스 안에서 해결하는 것을 목표로 합니다.

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

## 🗓 여행 일정 직접 편집

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

TripPilot의 첫 화면은
Google 검색 페이지처럼 복잡하지 않고 직관적인 UI를 목표로 합니다.

```text
              ✈️ TripPilot

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

필요한 사용자만 펼쳐서 설정할 수 있도록 구성합니다.

* 예상 예산
* 이동 수단
* 선호 음식
* 여행 강도
* 숙박 스타일
* 관심 테마

</details>

---

<details>
<summary><b>🚀 4. 개발 로드맵</b></summary>

<br>

# 1️⃣ 1차 구현

## MVP - AI 여행 가이드 & 일정 관리

### 구현 기능

* 메인 화면
* AI 여행 일정 생성
* 일정 생성 / 조회 / 수정 / 삭제
* 회원 기능
* 즐겨찾기
* 여행지 탐색

### 목표

> **AI 추천 + 사용자 직접 일정 관리**

TripPilot의 핵심 가치를 먼저 구현합니다.

---

# 2️⃣ 2차 구현

## 실시간 정보 & 예약 시스템

### 구현 기능

* 날씨 API
* 지도 API
* 관광 정보 API
* 숙박 정보
* 항공 / 교통 정보
* 관광지 티켓 예약
* 대기열 시스템
* 동시성 제어
* 부하 테스트

### 기술 목표

```text
대규모 사용자 접속
      ↓
대기열 시스템
      ↓
예약 요청 제어
      ↓
동시성 제어
      ↓
안정적인 예약 처리
```

### 목표

> **실제 서비스 환경에서 발생할 수 있는 트래픽 및 동시성 문제 해결**

---

# 3️⃣ 3차 구현

## RAG 기반 AI 여행 서비스

### 구현 기능

* AI 여행 챗봇
* RAG
* Vector Database
* 사용자 일정 분석
* 여행 동선 최적화
* 개인 맞춤형 여행 추천

### RAG Flow

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

### 목표

> **사용자의 여행 데이터를 이해하는 AI Travel Assistant 구현**

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
<summary><b>📂 7. 프로젝트 구조</b></summary>

<br>

```text
TripPilot/
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
├── docs/
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
<summary><b>👥 8. 팀원 구성</b></summary>

<br>

|    역할    |    이름   |
| :------: | :-----: |
|   👑 팀장  | **허민재** |
| 👨‍💻 팀원 |   정인길   |
| 👨‍💻 팀원 |   홍유원   |
| 👨‍💻 팀원 |   남호현   |

</details>

---

<details>
<summary><b>📈 9. 프로젝트를 통해 얻고자 하는 경험</b></summary>

<br>

## Web Development

* Spring Boot 기반 웹 서비스 설계
* REST API 설계
* 사용자 인증 및 권한 관리
* DB 모델링
* 외부 API 연동

## AI

* AI API 연동
* Local LLM 활용
* RAG 구조 설계
* Embedding 활용
* Vector Database 활용

## Performance

* Redis 활용
* 예약 동시성 문제 해결
* 대규모 트래픽 부하 테스트
* 대기열 시스템 설계

## Infrastructure

* AWS 서버 배포
* Nginx 구성
* Docker 활용
* GitHub Actions 기반 CI/CD

</details>

---

<details>
<summary><b>🔮 10. 향후 확장 기능</b></summary>

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
|  🎨 Figma | 추후 추가 |
| 📖 Notion | 추후 추가 |
|  🌐 Demo  | 추후 추가 |
|  🎬 Video | 추후 추가 |

---

<div align="center">

### 🌍 Start Your Journey with TripPilot

**AI-powered travel planning for your perfect journey.**

<br>

✈️   🗺️   🌴   🏨   📍

<br><br>

**TripPilot Team**

</div>

<img src="https://capsule-render.vercel.app/api?type=waving&color=00C9A7&height=160&section=footer&text=Ready%20for%20Your%20Next%20Journey&fontSize=26&fontColor=ffffff&animation=fadeIn" width="100%" />
```
