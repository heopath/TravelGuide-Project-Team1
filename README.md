<img src="https://capsule-render.vercel.app/api?type=waving&color=0:4FACFE,100:00C9A7&height=260&section=header&text=TripPilot&fontSize=58&fontColor=ffffff&animation=fadeIn&desc=AI-Powered%20Travel%20Guide%20%26%20Planner&descSize=20&descAlignY=65" width="100%" />

<div align="center">

# ✈️ TripPilot

### AI 기반 맞춤형 여행 가이드 & 여행 일정 플래닝 서비스

사용자의 **여행 기간, 목적지, 동행자, 여행 스타일**을 기반으로  
AI가 여행 일정을 추천하고, 사용자가 직접 일정을 수정·완성할 수 있는 여행 플랫폼입니다.

<br>

<img src="https://img.shields.io/badge/Project-TripPilot-00C9A7?style=for-the-badge" />
<img src="https://img.shields.io/badge/Java-17-007396?style=for-the-badge&logo=openjdk&logoColor=white" />
<img src="https://img.shields.io/badge/Spring_Boot-6DB33F?style=for-the-badge&logo=springboot&logoColor=white" />
<img src="https://img.shields.io/badge/MySQL-4479A1?style=for-the-badge&logo=mysql&logoColor=white" />
<img src="https://img.shields.io/badge/AI-Travel_Planner-8A2BE2?style=for-the-badge" />
<img src="https://img.shields.io/badge/Status-Planning-orange?style=for-the-badge" />

</div>

---

## 📌 Project Summary

TripPilot은 단순한 여행 추천 서비스가 아니라,  
**AI 추천 → 사용자 일정 편집 → 실시간 여행 정보 → 예약 → RAG 기반 개인화 여행 가이드**  
순으로 확장되는 프로젝트입니다.

### 핵심 목표
- AI가 사용자의 조건에 맞는 여행 일정을 추천
- 사용자가 추천 일정을 직접 수정하고 저장
- 실시간 날씨/지도/관광 정보를 반영
- 예약 및 대기열 시스템으로 실서비스형 기능 확장
- RAG 기반 AI 챗봇으로 개인화 여행 가이드 구현

---

<details>
<summary><b>📖 1. 프로젝트 소개</b></summary>

<br>

### 📝 프로젝트 개요
여행을 준비할 때 사용자는 여행지, 관광지, 맛집, 숙박, 교통, 날씨 등을 각각 따로 찾아야 합니다.  
TripPilot은 이러한 과정을 하나의 서비스로 통합하여,  
사용자가 간단한 조건만 입력하면 AI가 여행 계획을 제안하고 사용자가 직접 수정할 수 있도록 돕는 서비스입니다.

### 🎯 프로젝트 방향
- **1차 구현**: AI 여행 일정 추천 + 일정 관리
- **2차 구현**: 실시간 정보 + 예약/대기열 시스템
- **3차 구현**: RAG 기반 개인화 AI 여행 가이드

### 💡 서비스 한 줄 소개
> **“AI가 추천하고, 사용자가 완성하는 여행 플래너”**

</details>

---

<details>
<summary><b>🛠️ 2. 기술 스택</b></summary>

<br>

## Backend
<img src="https://img.shields.io/badge/java%2017-007396?style=for-the-badge&logo=openjdk&logoColor=white">
<img src="https://img.shields.io/badge/spring%20boot-6DB33F?style=for-the-badge&logo=springboot&logoColor=white">
<img src="https://img.shields.io/badge/spring%20security-6DB33F?style=for-the-badge&logo=springsecurity&logoColor=white">
<img src="https://img.shields.io/badge/jpa-59666C?style=for-the-badge">
<img src="https://img.shields.io/badge/mybatis-000000?style=for-the-badge">

## Frontend
<img src="https://img.shields.io/badge/html5-E34F26?style=for-the-badge&logo=html5&logoColor=white">
<img src="https://img.shields.io/badge/css3-1572B6?style=for-the-badge&logo=css3&logoColor=white">
<img src="https://img.shields.io/badge/javascript-F7DF1E?style=for-the-badge&logo=javascript&logoColor=black">
<img src="https://img.shields.io/badge/thymeleaf-005F0F?style=for-the-badge&logo=thymeleaf&logoColor=white">

## Database / Cache
<img src="https://img.shields.io/badge/mysql-4479A1?style=for-the-badge&logo=mysql&logoColor=white">
<img src="https://img.shields.io/badge/redis-DC382D?style=for-the-badge&logo=redis&logoColor=white">

## AI / Data
<img src="https://img.shields.io/badge/ollama-000000?style=for-the-badge">
<img src="https://img.shields.io/badge/gemini%20api-4285F4?style=for-the-badge&logo=google&logoColor=white">
<img src="https://img.shields.io/badge/rag-8A2BE2?style=for-the-badge">
<img src="https://img.shields.io/badge/vector%20db-4B0082?style=for-the-badge">

## Infra / Collaboration
<img src="https://img.shields.io/badge/aws%20ec2-FF9900?style=for-the-badge&logo=amazonec2&logoColor=white">
<img src="https://img.shields.io/badge/nginx-009639?style=for-the-badge&logo=nginx&logoColor=white">
<img src="https://img.shields.io/badge/docker-2496ED?style=for-the-badge&logo=docker&logoColor=white">
<img src="https://img.shields.io/badge/github%20actions-2088FF?style=for-the-badge&logo=githubactions&logoColor=white">
<img src="https://img.shields.io/badge/figma-F24E1E?style=for-the-badge&logo=figma&logoColor=white">

</details>

---

<details>
<summary><b>✨ 3. 주요 기능</b></summary>

<br>

### 1) AI 여행 일정 추천
사용자가 입력한 여행 조건을 기반으로 AI가 맞춤형 일정을 생성합니다.

- 여행 지역
- 여행 기간
- 동행자
- 여행 스타일
- 여행 목적
- 예상 예산

#### 예시 입력
```text
여행 지역 : 제주도
여행 기간 : 3박 4일
동행자 : 친구
여행 스타일 : 맛집 + 관광
예산 : 1인 50만원
```

#### 예시 결과
```text
DAY 1
제주공항 → 동문시장 → 용두암 → 숙소 체크인

DAY 2
성산일출봉 → 섭지코지 → 우도

DAY 3
협재해수욕장 → 오설록 → 애월 카페거리

DAY 4
기념품 쇼핑 → 제주공항
```

---

### 2) 여행 일정 직접 편집
AI가 추천한 일정은 사용자가 자유롭게 수정할 수 있습니다.

- 일정 추가 / 삭제
- 일정 순서 변경
- 관광지 / 맛집 / 숙소 추가
- 메모 작성
- 내 일정 저장

---

### 3) 여행 장소 탐색
여행지의 다양한 정보를 검색하고 일정에 추가할 수 있습니다.

- 관광지
- 맛집
- 카페
- 숙박
- 축제 / 체험 활동

---

### 4) 즐겨찾기
관심 있는 장소를 저장하고 나중에 일정에 추가할 수 있습니다.

- 관광지 즐겨찾기
- 맛집 즐겨찾기
- 카페 즐겨찾기

---

### 5) 회원 기능
- 회원가입 / 로그인 / 로그아웃
- 마이페이지
- 내 일정 조회
- 즐겨찾기 관리

</details>

---

<details>
<summary><b>🗺️ 4. 서비스 흐름</b></summary>

<br>

```text
여행 조건 입력
      ↓
AI 여행 일정 생성
      ↓
사용자 일정 수정
      ↓
실시간 여행 정보 확인
      ↓
숙박 / 교통 / 티켓 조회
      ↓
여행 계획 저장 및 관리
      ↓
RAG 기반 AI 여행 상담
```

### 메인 화면 기획 방향
- Google 첫 화면처럼 **단순하고 직관적인 UI**
- 복잡한 기능은 버튼 클릭 시 펼쳐지는 구조
- 처음 사용해도 쉽게 이해할 수 있는 입력 흐름
- 여행 느낌이 나는 밝고 시원한 디자인 적용

#### 메인 화면 예시 구조
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

</details>

---

<details>
<summary><b>🚀 5. 개발 로드맵</b></summary>

<br>

## 1차 구현 - MVP
### AI 여행 가이드 & 일정 관리
- 메인 화면
- AI 여행 일정 추천
- 일정 생성 / 조회 / 수정 / 삭제
- 회원 기능
- 즐겨찾기

### 목표
> **TripPilot의 핵심 가치인 AI 추천 + 사용자 일정 관리 기능 구현**

---

## 2차 구현 - 서비스 확장
### 실시간 정보 & 예약 시스템
- 날씨 API
- 지도 API
- 관광 정보 API
- 숙박 정보
- 항공 / 교통 정보
- 관광지 티켓 예약
- 대기열 시스템
- 동시성 제어
- 부하 테스트

### 목표
> **실제 서비스 환경에서 발생할 수 있는 트래픽 및 동시성 문제 해결**

---

## 3차 구현 - AI 고도화
### RAG 기반 여행 AI
- RAG 기반 여행 챗봇
- Vector DB 활용
- 사용자 일정 분석
- 여행 동선 최적화
- 개인 맞춤형 여행 추천

### 목표
> **개인화된 AI Travel Assistant 구현**

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

```plaintext
TripPilot/
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

| 역할 | 이름 |
|:---:|:---:|
| 팀장 | 허민재 |
| 팀원 | 정인길 |
| 팀원 | 홍유원 |
| 팀원 | 남호현 |

</details>

---

<details>
<summary><b>📈 9. 기대 효과</b></summary>

<br>

### Web Development
- Spring Boot 기반 웹 서비스 설계
- REST API 설계
- 사용자 인증 및 권한 관리
- DB 모델링 및 데이터 처리

### AI
- AI API 연동
- LLM 기반 서비스 구현
- RAG 구조 설계
- Vector Database 활용

### Performance
- Redis 활용
- 예약 동시성 제어
- 대규모 트래픽 부하 테스트
- 대기열 시스템 설계

### Infrastructure
- AWS 서버 배포
- Nginx 구성
- Docker 활용
- GitHub Actions 기반 CI/CD

</details>

---

<details>
<summary><b>🔮 10. 향후 확장 기능</b></summary>

<br>

- 개인 맞춤형 여행 추천
- 실시간 날씨 기반 일정 변경
- 지도 기반 동선 최적화
- 숙박 및 항공 예약 연동
- 관광지 티켓 예약
- Redis 기반 예약 시스템
- 대규모 트래픽 대기열 처리
- 사용자 여행 패턴 분석
- RAG 기반 여행 챗봇

</details>

---

<div align="center">

### ✈️ TripPilot
**Your AI-Powered Travel Guide & Planner**

AI가 추천하고,  
사용자가 완성하는  
똑똑한 여행 계획 서비스

</div>

<img src="https://capsule-render.vercel.app/api?type=waving&color=0:00C9A7,100:4FACFE&height=160&section=footer" width="100%" />
