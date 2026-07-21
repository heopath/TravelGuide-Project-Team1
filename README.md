<img src="https://capsule-render.vercel.app/api?type=waving&color=0:1E3A5F,50:2563EB,100:38BDF8&height=250&section=header&text=GroupWare%20Project&fontSize=55&fontColor=FFFFFF&animation=fadeIn&desc=Smart%20Work%20%26%20AI%20Collaboration%20Platform&descSize=20&descAlignY=65" width="100%" />

<div align="center">

# 🏢 GroupWare Project

### 업무와 협업을 하나로 연결하는 스마트 그룹웨어 플랫폼

**전자결재 · 일정관리 · 게시판 · 조직관리 · 업무관리 · AI 업무 도우미**

<br>

![Java](https://img.shields.io/badge/Java-17-007396?style=flat-square\&logo=openjdk\&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-6DB33F?style=flat-square\&logo=springboot\&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1?style=flat-square\&logo=mysql\&logoColor=white)
![GitHub](https://img.shields.io/badge/GitHub-Collaboration-181717?style=flat-square\&logo=github\&logoColor=white)

</div>

---

## 📑 목차

1. [프로젝트 소개](#-프로젝트-소개)
2. [프로젝트 목표](#-프로젝트-목표)
3. [핵심 기능](#-핵심-기능)
4. [AI 및 외부 API 활용](#-ai-및-외부-api-활용)
5. [기술 스택](#️-기술-스택)
6. [팀원 소개](#-팀원-소개)
7. [시스템 아키텍처](#️-시스템-아키텍처)
8. [프로젝트 폴더 구조](#-프로젝트-폴더-구조)
9. [Git 브랜치 전략](#-git-브랜치-전략)
10. [성능 및 부하 테스트](#-성능-및-부하-테스트)
11. [개발 진행 순서](#-개발-진행-순서)

---

<details open>
<summary><b>📌 프로젝트 소개</b></summary>

<br>

## 📝 프로젝트 소개

**GroupWare Project**는 기업 구성원들이 하나의 시스템에서
업무와 협업을 효율적으로 처리할 수 있도록 구현하는 **통합 그룹웨어 웹 서비스**입니다.

사내 구성원의 업무 환경을 고려하여 회원 및 조직 관리부터 전자결재, 일정 관리, 게시판, 업무 관리, 알림 등의 기능을 하나의 플랫폼으로 통합하는 것을 목표로 합니다.

또한 기존 그룹웨어 기능 구현에 그치지 않고 **AI 업무 도우미**, **실시간 외부 데이터 API**, **대규모 동시 접속을 가정한 부하 테스트** 등을 적용하여 실제 서비스 환경에서 발생할 수 있는 상황까지 경험하는 것을 목표로 합니다.

> 단순 CRUD 웹 프로젝트가 아닌
> **기업 업무 프로세스 + AI + 외부 API + 시스템 운영 경험**을 함께 구현하는 프로젝트입니다.

</details>

---

<details open>
<summary><b>🎯 프로젝트 목표</b></summary>

<br>

## 🎯 프로젝트 목표

### 01. 업무 프로세스 통합

전자결재, 일정, 게시판, 업무 관리 등 기업에서 사용하는 주요 업무 기능을 하나의 서비스에서 제공합니다.

### 02. 사용자 권한 관리

일반 사원, 부서 관리자, 시스템 관리자 등의 권한을 구분하여 역할에 따른 기능 접근을 제어합니다.

### 03. AI 업무 생산성 향상

로컬 LLM 또는 AI API를 연동하여 문서 요약, 업무 질문, 문서 초안 작성 등의 기능을 제공합니다.

### 04. 실시간 외부 정보 제공

날씨, 유가 등 외부 API를 활용하여 사용자가 필요한 정보를 그룹웨어 내부에서 바로 확인할 수 있도록 구현합니다.

### 05. 실제 운영 환경 경험

동시 접속자가 증가하는 상황을 가정한 부하 테스트를 진행하고 서버 성능 및 장애 대응 방법을 분석합니다.

</details>

---

<details open>
<summary><b>✨ 핵심 기능</b></summary>

<br>

## ✨ 핵심 기능

| 영역                  | 주요 기능                              |
| ------------------- | ---------------------------------- |
| 👤 **회원 / 조직 관리**   | 로그인, 회원 관리, 부서 관리, 직급 관리, 조직도      |
| 📝 **전자결재**         | 결재 문서 작성, 결재선 설정, 승인, 반려, 결재 상태 조회 |
| 📅 **일정 관리**        | 개인 일정, 부서 일정, 회사 일정, 캘린더           |
| 💼 **업무 관리**        | 업무 등록, 담당자 지정, 진행 상태 관리            |
| 📢 **게시판**          | 공지사항, 사내 게시판, 자료 공유                |
| 📁 **파일 관리**        | 업무 관련 파일 업로드 및 첨부파일 관리             |
| 🔔 **알림**           | 결재 요청, 업무 변경, 일정 등의 알림             |
| 🏢 **관리자**          | 회원, 조직, 권한, 시스템 설정 관리              |
| 🤖 **AI Assistant** | 업무 질문, 문서 요약, 문서 작성 지원             |
| 🌤️ **외부 API**      | 실시간 날씨 및 생활·업무 관련 정보 조회            |

</details>

---

<details>
<summary><b>🤖 AI 및 외부 API 활용</b></summary>

<br>

## 🤖 AI 및 외부 API 활용

### 🧠 AI 업무 도우미

그룹웨어 내부에서 AI와 대화할 수 있는 업무 지원 기능을 구현합니다.

```text
사용자 질문
    │
    ▼
GroupWare
    │
    ▼
Spring Boot
    │
    ├──── Ollama / Local LLM
    │
    └──── External AI API
    │
    ▼
AI Response
```

### AI 활용 예시

* 사내 공지사항 및 문서 요약
* 업무 내용 요약
* 이메일 및 보고서 초안 생성
* 회의 내용 정리
* 그룹웨어 사용 방법 질의응답
* 사내 규정 기반 질의응답
* 업무 관련 AI 챗봇

### 🦙 Ollama

로컬 환경에서 LLM을 실행하여 AI 기능을 제공합니다.

```text
GroupWare
    │
    ▼
Spring Boot
    │
    ▼
Ollama
    │
    ▼
Local LLM
```

API 사용 비용 없이 테스트할 수 있으며
향후 사내 문서를 활용한 **RAG 기반 AI 검색 기능**으로 확장할 수 있습니다.

---

### 🌐 External API

AI가 자체적으로 제공하기 어려운 실시간 정보는 외부 API와 연동합니다.

| API          | 활용 기능             |
| ------------ | ----------------- |
| 🌤️ 날씨 API   | 현재 날씨 및 지역별 날씨 조회 |
| ⛽ 유가 API     | 지역별 주유소 및 유가 정보   |
| 🗺️ 지도 API   | 회사 및 외근 위치 정보     |
| 📅 공공데이터 API | 공휴일 및 기타 공공 정보    |

```text
사용자
   │
   ▼
GroupWare
   │
   ├──── AI Server (Ollama)
   │
   ├──── Weather API
   │
   ├──── Oil Price API
   │
   └──── Public Data API
   │
   ▼
통합 업무 정보 제공
```

</details>

---

<details open>
<summary><b>🛠️ 기술 스택</b></summary>

<br>

## 🛠️ 기술 스택

### 🎨 Frontend

<p>
<img src="https://img.shields.io/badge/HTML5-E34F26?style=for-the-badge&logo=html5&logoColor=white">
<img src="https://img.shields.io/badge/CSS3-1572B6?style=for-the-badge&logo=css3&logoColor=white">
<img src="https://img.shields.io/badge/JavaScript-F7DF1E?style=for-the-badge&logo=javascript&logoColor=black">
<img src="https://img.shields.io/badge/Thymeleaf-005F0F?style=for-the-badge&logo=thymeleaf&logoColor=white">
</p>

### ⚙️ Backend

<p>
<img src="https://img.shields.io/badge/Java-17-007396?style=for-the-badge&logo=openjdk&logoColor=white">
<img src="https://img.shields.io/badge/Spring%20Boot-6DB33F?style=for-the-badge&logo=springboot&logoColor=white">
<img src="https://img.shields.io/badge/Spring%20Security-6DB33F?style=for-the-badge&logo=springsecurity&logoColor=white">
</p>

### 🗄️ Database

<p>
<img src="https://img.shields.io/badge/MySQL-4479A1?style=for-the-badge&logo=mysql&logoColor=white">
</p>

### 🤖 AI

<p>
<img src="https://img.shields.io/badge/Ollama-000000?style=for-the-badge&logo=ollama&logoColor=white">
<img src="https://img.shields.io/badge/LLM-AI%20Assistant-8A2BE2?style=for-the-badge">
</p>

### ☁️ Infra & DevOps

<p>
<img src="https://img.shields.io/badge/AWS%20EC2-FF9900?style=for-the-badge&logo=amazonec2&logoColor=white">
<img src="https://img.shields.io/badge/AWS%20RDS-527FFF?style=for-the-badge&logo=amazonrds&logoColor=white">
<img src="https://img.shields.io/badge/Nginx-009639?style=for-the-badge&logo=nginx&logoColor=white">
<img src="https://img.shields.io/badge/GitHub%20Actions-2088FF?style=for-the-badge&logo=githubactions&logoColor=white">
</p>

### 🤝 Tools & Collaboration

<p>
<img src="https://img.shields.io/badge/Git-F05032?style=for-the-badge&logo=git&logoColor=white">
<img src="https://img.shields.io/badge/GitHub-181717?style=for-the-badge&logo=github&logoColor=white">
<img src="https://img.shields.io/badge/Figma-F24E1E?style=for-the-badge&logo=figma&logoColor=white">
<img src="https://img.shields.io/badge/Notion-000000?style=for-the-badge&logo=notion&logoColor=white">
</p>

</details>

---

<details open>
<summary><b>👥 팀원 소개</b></summary>

<br>

## 👥 팀원 소개

<div align="center">

|    이름   |         역할         | 담당 영역              |
| :-----: | :----------------: | :----------------- |
| **허민재** | 👑 **Team Leader** | 프로젝트 총괄 및 담당 기능 개발 |
| **정인길** |  👨‍💻 Team Member | 담당 기능 개발           |
| **홍유원** |  👨‍💻 Team Member | 담당 기능 개발           |
| **남호현** |  👨‍💻 Team Member | 담당 기능 개발           |

</div>

### 🤝 협업 방식

* 기능별 담당 영역을 구분하여 개발
* GitHub Issue를 활용한 작업 관리
* Feature Branch 기반 기능 개발
* Pull Request를 통한 코드 병합
* 공통 기능 수정 시 팀원 간 사전 공유
* Notion을 활용한 일정 및 진행 상황 관리

> 팀원별 세부 담당 기능은 프로젝트 기능 분배 후 업데이트 예정입니다.

</details>

---

<details>
<summary><b>🏗️ 시스템 아키텍처</b></summary>

<br>

## 🏗️ 시스템 아키텍처

```text
                     ┌──────────────────┐
                     │       User       │
                     │   Web Browser    │
                     └────────┬─────────┘
                              │
                              ▼
                     ┌──────────────────┐
                     │      Nginx       │
                     │  Reverse Proxy   │
                     └────────┬─────────┘
                              │
                              ▼
                 ┌────────────────────────┐
                 │      Spring Boot       │
                 │   GroupWare Server     │
                 └───────┬──────┬─────────┘
                         │      │
                  ┌──────▼───┐  └───────────────┐
                  │  MySQL   │                  │
                  │ Database │                  ▼
                  └──────────┘         ┌────────────────┐
                                       │     Ollama     │
                                       │   Local LLM    │
                                       └────────────────┘
                                               
                         │
                         ▼
             ┌───────────────────────────┐
             │       External API        │
             │ Weather · Oil · Public API│
             └───────────────────────────┘
```

### 🚀 CI/CD

```text
Developer
    │
    ▼
GitHub Push
    │
    ▼
GitHub Actions
    │
    ▼
Gradle Build
    │
    ▼
AWS EC2 Deploy
    │
    ▼
Application Restart
```

</details>

---

<details>
<summary><b>📁 프로젝트 폴더 구조</b></summary>

<br>

## 📁 프로젝트 폴더 구조

```plaintext
GroupWare-Project/
│
├── src/
│   ├── main/
│   │   │
│   │   ├── java/
│   │   │   └── com/groupware/
│   │   │       │
│   │   │       ├── config/
│   │   │       ├── controller/
│   │   │       ├── service/
│   │   │       ├── repository/
│   │   │       ├── entity/
│   │   │       ├── dto/
│   │   │       │
│   │   │       ├── member/
│   │   │       ├── organization/
│   │   │       ├── approval/
│   │   │       ├── schedule/
│   │   │       ├── task/
│   │   │       ├── board/
│   │   │       ├── notification/
│   │   │       ├── admin/
│   │   │       └── ai/
│   │   │
│   │   └── resources/
│   │       │
│   │       ├── templates/
│   │       │   ├── member/
│   │       │   ├── approval/
│   │       │   ├── schedule/
│   │       │   ├── task/
│   │       │   ├── board/
│   │       │   ├── admin/
│   │       │   └── ai/
│   │       │
│   │       ├── static/
│   │       │   ├── css/
│   │       │   ├── js/
│   │       │   └── images/
│   │       │
│   │       └── application.yml
│   │
│   └── test/
│
├── build.gradle
├── README.md
└── .gitignore
```

</details>

---

<details>
<summary><b>🔀 Git 브랜치 전략</b></summary>

<br>

## 🔀 Git 브랜치 전략

팀원별 기능 브랜치를 생성하여 기능 개발 후 Pull Request를 통해 병합합니다.

```text
main
 │
 ├── develop
 │     │
 │     ├── feature/minjae
 │     ├── feature/ingil
 │     ├── feature/yuwon
 │     └── feature/hohyeon
 │
 └── release
```

### Branch 역할

| Branch      | 설명           |
| ----------- | ------------ |
| `main`      | 최종 배포 버전     |
| `develop`   | 개발 기능 통합     |
| `feature/*` | 개인 또는 기능별 개발 |
| `release`   | 배포 전 최종 테스트  |

### Git 작업 규칙

```bash
# 개발 브랜치 최신화
git checkout develop
git pull origin develop

# 기능 브랜치 생성
git checkout -b feature/기능명

# 작업 후 Commit
git add .
git commit -m "feat: 기능 설명"

# Remote Push
git push origin feature/기능명
```

이후 GitHub에서 Pull Request를 생성하여 `develop` 브랜치에 병합합니다.

### Commit Convention

| Type       | 설명          |
| ---------- | ----------- |
| `feat`     | 새로운 기능 추가   |
| `fix`      | 버그 수정       |
| `design`   | UI / CSS 수정 |
| `refactor` | 코드 리팩토링     |
| `docs`     | 문서 수정       |
| `test`     | 테스트 코드      |
| `chore`    | 설정 및 기타 작업  |

```text
feat: 전자결재 문서 작성 기능 추가
fix: 로그인 세션 오류 수정
design: 그룹웨어 메인 대시보드 UI 수정
refactor: 결재 서비스 로직 분리
docs: README 프로젝트 구조 업데이트
```

</details>

---

<details>
<summary><b>🔥 성능 및 부하 테스트</b></summary>

<br>

## 🔥 성능 및 부하 테스트

실제 서비스 환경에서 다수의 사용자가 동시에 그룹웨어에 접속하는 상황을 가정하여 부하 테스트를 진행합니다.

### 테스트 시나리오

```text
1 User
   │
   ▼
100 Users
   │
   ▼
500 Users
   │
   ▼
1,000+ Concurrent Users
   │
   ▼
Server Monitoring
```

### 주요 테스트 항목

* 로그인 동시 요청
* 그룹웨어 메인 페이지 동시 접속
* 게시판 목록 조회
* 전자결재 목록 조회
* 일정 조회
* AI 기능 동시 요청
* DB Connection 증가 상황
* 서버 CPU 및 Memory 사용량
* 평균 응답 시간
* 오류 발생률

### 테스트 목적

```text
부하 발생
   │
   ▼
병목 지점 확인
   │
   ├── Application
   ├── Database
   ├── Network
   └── AI Server
   │
   ▼
성능 개선
   │
   ▼
재테스트
```

향후 **JMeter 또는 k6** 등을 활용하여 동시 접속 테스트를 진행하고 결과를 분석할 예정입니다.

이를 통해 단순 기능 구현을 넘어 실제 서비스 운영 과정에서 발생할 수 있는
**트래픽 증가, 서버 과부하, 응답 지연 및 장애 상황을 경험하고 개선하는 것**을 목표로 합니다.

</details>

---

<details>
<summary><b>📅 개발 진행 순서</b></summary>

<br>

## 📅 개발 진행 순서

### 01. 프로젝트 기획

* 그룹웨어 주요 기능 정의
* 사용자 역할 및 권한 정의
* 요구사항 분석

### 02. 화면 설계

* 정보구조(IA) 작성
* 사용자 Flow Chart 작성
* Figma 화면 설계
* 공통 UI 디자인 정의

### 03. 데이터베이스 설계

* 주요 Entity 정의
* ERD 작성
* 테이블 관계 설정
* DDL 작성

### 04. 프로젝트 개발

* 공통 레이아웃 구현
* 회원 및 조직 관리
* 전자결재
* 일정 관리
* 업무 관리
* 게시판
* 알림
* 관리자 기능

### 05. AI 및 외부 API 연동

* Ollama 기반 AI 연동
* AI 챗봇 UI 구현
* 실시간 날씨 API
* 외부 정보 API 연동

### 06. 시스템 통합

* 기능 통합 테스트
* 권한 및 보안 테스트
* 오류 처리

### 07. 배포

* AWS 서버 구성
* Nginx Reverse Proxy
* GitHub Actions CI/CD

### 08. 성능 테스트

* 동시 접속 부하 테스트
* 서버 자원 모니터링
* 병목 구간 분석
* 성능 개선

---

## 🚀 프로젝트 핵심 키워드

<div align="center">

`GroupWare` · `Spring Boot` · `Collaboration` · `Electronic Approval`

`AI Assistant` · `Ollama` · `External API` · `Load Testing`

`AWS` · `CI/CD` · `GitHub Actions`

</div>

---

<div align="center">

### 👨‍💻 Team

**Team Leader | 허민재**

**Team Member | 정인길 · 홍유원 · 남호현**

<br>

> **Better Work, Better Collaboration.**
> 업무와 협업을 더 스마트하게 연결합니다.

</div>

<img src="https://capsule-render.vercel.app/api?type=waving&color=0:1E3A5F,50:2563EB,100:38BDF8&height=150&section=footer" width="100%" />
