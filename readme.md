# ShoesBox Back-End

[서비스 소개 readme 링크](https://github.com/shoesbox)

## 목차

[1. 기술 스택](#1-기술-스택)

[2. 서비스 아키텍쳐](#2-서비스-아키텍쳐)

[3. Git Branch 전략](#3-git-branch-전략)

[4. 코드 컨벤션](#4-코드-컨벤션)

[5. 트러블 슈팅](#5-트러블-슈팅)

[6. 팀원들의 회고](#6-팀원들의-회고)

---

## 1. 기술 스택

* Java 11
* Spring Boot
* AWS
  * EC2
  * S3
  * RDS (MySQL)
* JWT
* Thumbnailator (WebP)
* NginX
* Redis

---

## 2. 서비스 아키텍쳐

![아키텍쳐](/img/architecture.png)

---

## 3. Git Branch 전략

<details>
<summary>더 보기</summary>

### GitHub Flow 사용

![GitHub Flow](/img/github-flow.png)

### GitHub Flow를 선택한 이유

* 대안 중 하나였던 **Git Flow**는 팀이 처한 상황에 비해 지나치게 복잡하다고 판단했습니다.
  * *ShoesBox는 현재 서비스 중인 프로젝트도 아니고, 장기적으로 서비스하며 관리할 프로젝트가 아님.*
  * *hotfix, release 등의 브랜치가 필요할 만큼 상황이 급박하거나, 프로젝트가 거대하지 않음.*
* **GitHub Flow**는 단순하지만 **GitHub**의 장점(ex. PR 등) 대부분을 활용 가능하고, 브랜치 전략이 복잡해서 발생하는 ***프로젝트 오버헤드를 최소화***

### GitHub Flow

* main 브랜치에서 개발이 시작된다.
* 기능 구현이나 버그가 발생하면 issue를 작성한다.
* 팀원들이 issue 해결을 위해 main 브랜치에서 생성한 브랜치에서 개발을 하고 commit log를 작성한다.
  * 브랜치명은 목적이 명확하게 드러나도록 작성해야 한다.
  * ex) feature/{issue-number}-{feature-name}
* 정기적으로 원격 브랜치에 push한다.
  * 팀원들이 확인하기 쉽고, 로컬에 문제가 발생했을 때 되돌리기 쉽다.
* 도움, 피드백이 필요하거나 기능이 완성되면 pull request를 생성한다.
  * PR을 통해 팀원들 간의 피드백, 버그 찾기 등이 진행된다. ***release 브랜치가 없으므로 이 과정이 매우 중요하다.***
* main 브랜치에 생성된 PR은 Actions를 통해 자동으로 빌드 테스트가 수행된다.
* 모든 리뷰가 이루어지면, merge하기 전에 최종 테스트를 진행한다.
* 테스트까지 완료되면 main 브랜치에 merge 후 push 한다.
* 병합된 main 브랜치는 Actions를 통해 자동으로 빌드, 및 배포된다. (`AWS Code Deploy`)
* merge한 이후 PR을 요청한 브랜치는 즉시 삭제한다.
  * 작업이 완료되었음을 의미
  * 누군가 실수로 오래된 브랜치를 사용하는 것을 방지
  * *필요시 삭제한 브랜치의 복구도 가능*

### 커밋 메시지 컨벤션

코드 컨벤션 문서 참고

</details>

---

## 4. 코드 컨벤션

[코드 컨벤션 링크](/convention.md)

---

## 5. 트러블 슈팅

<details>

<summary style="font-size: large; font-weight: bold; line-height: 300%">메인 페이지(달력) 게시글(일기) 목록 로딩 속도 저하</summary>

* 이슈:
  * 메인 페이지에서 게시글 목록을 달력 형태로 보여주고 있었고, 게시글에 이미지가 첨부되어 있다면 달력 칸에 맞춰서 썸네일 형태로 보여주고 있었다. 그런데 메인 페이지에서 보여지는 게시글 양이
    많아질수록, 로딩속도가 느려지기 시작했다. UX를 위해서라도 속도 개선이 필요했다.
* 원인:
  * 원인은 게시글에 첨부된 이미지였다. 아무런 처리를 하지 않았기 때문에 실제 첨부된 이미지를 url을 통해 불러오고 있었던 것이다. 크기를 줄여 썸네일 이미지를 따로 만든다면 로딩 속도를 개선할 수 있다고 판단했다.
* 해결:
  * 게시글을 작성할 때 첫 번째로 첨부된 이미지를 리사이징 하여 따로 저장한 뒤, 메인 페이지에서 해당 썸네일 url을 반환했다. 이미지 크기가 감소한 만큼 파일 용량 또한 감소했고, 그만큼 로딩 속도도 개선되었다.
  * 처음엔 자바 내장 라이브러리인 `Graphics2D`를 사용해 썸네일을 제작했으나, 이미지 품질이 눈에 띄게 저하되는 문제가 있었다. 결국 오픈소스 라이브러리인 `Thumbnailator`를 사용하기로 결정했다.
  * `Imgscalr`, `Marvin` 등 여러 대안이 있었지만, `Thumbnailator`가 가장 많이 사용되고, 또한 비교적 최근까지 업데이트가 되고 있다는 점을 고려했다.

</details>

<details>

<summary style="font-size: large; font-weight: bold; line-height: 300%">이미지 처리 개선</summary>

* 이슈:
  * 앞서 개발한 썸네일 기능 덕분에 메인 페이지는 별 다른 문제 없이 돌아가고 있었지만, 게시글 상세조회를 할 때 문제가 발생했다. 상세 조회 페이지에서는 원본 이미지를 그대로 보여주고 있었기 때문에, 원본 이미지의 크기가 클 경우 로딩 속도 저하 문제가 발생했다.
* 해결:
  * 먼저 이미지 첨부 개수 제한을 두었으나, 여전히 고해상도 이미지 첨부 시 로딩 속도가 상당히 저하되었다.
  * 결국 이미지를 압축해서 저장하기로 결정했고, 화질저하를 피하고 파일 크기를 줄이기 위해 WebP 포맷을 사용하기로 결정했다.
  * 앞서 적용한 `Thumbnailator`가 자바 내장 라이브러리인 `Java I/O`를 기반으로 돌아가는 라이브러리였다. 때문에 `Java I/O`에 WebP 플러그인을 추가하는 것만으로도 `Thumbnailator`를 사용해서 WebP 인코딩, 리사이징 모두 가능해졌다.
  * 그렇게 썸네일을 포함한 모든 첨부 이미지를 WebP 포맷으로 인코딩해서 서버에 저장했고, 큰 화질 저하 없이 이미지 파일 크기가 대폭 감소되었다.
  * 의도했던 로딩 속도 개선은 물론, 덤으로 서버(s3)의 저장 공간 역시 절약되어 2가지 효과를 보았다.

</details>

---

## 6. 팀원들의 회고

정찬호 - [실전 프로젝트 회고록](https://github.com/Elrendar/TIL/blob/main/WIL/221009_%EC%8B%A4%EC%A0%84-%ED%94%84%EB%A1%9C%EC%A0%9D%ED%8A%B8-Final.md)
