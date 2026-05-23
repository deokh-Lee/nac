# NAC Spring Boot MyBatis MariaDB Document Extraction

`CN_ELEC_DOC` 테이블의 전자문서 파일을 읽어서 텍스트를 추출하고, 결과를 `EXTRACT_ELEC_DOC` 테이블에 적재하는 Spring Boot + MyBatis 프로젝트입니다.

## 기술 스택

- Java 17
- Spring Boot 3.3.5
- MyBatis Spring Boot Starter
- MariaDB Java Client
- Apache Tika
- Maven

## DB 접속 정보

현재 `src/main/resources/application.yml`에는 아래 접속 정보가 반영되어 있습니다.

```yaml
spring:
  datasource:
    driver-class-name: org.mariadb.jdbc.Driver
    url: jdbc:mariadb://192.168.250.25:33306/ARCHIVES_PUB_DB?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Seoul
    username: root
    password: root
```

## 문서 파일 경로 규칙

기본 파일 저장소 경로는 다음과 같습니다.

```yaml
document:
  extract:
    base-path: /data/file-data/elec_doc
    default-transfer-year: 2023
    batch-size: 100
```

코드는 아래 규칙으로 실제 파일 경로를 만듭니다.

```text
/data/file-data/elec_doc/{TRANSFERYEAR}/{RC_RFILE_NO}/{RC_RITEM_NO}/{SAVE_FILE_NAME 또는 ORG_FILE_NAME}
```

예:

```text
/data/file-data/elec_doc/2023/202311293320/000000000073/202311293320_000000000073_N01.hwp
```

## MyBatis 설정

```yaml
mybatis:
  mapper-locations: classpath:/mapper/**/*.xml
  type-aliases-package: com.saltlux.nac.record,com.saltlux.nac.elecdoc
  configuration:
    map-underscore-to-camel-case: true
```

## 주요 처리 흐름

1. `CN_ELEC_DOC`에서 대상 문서 목록 조회
2. 파일 경로 생성
3. Apache Tika로 HWP, Office, PDF 등 텍스트 추출 시도
4. `EXTRACT_ELEC_DOC`에 결과 upsert
5. 성공 시 `EXTRACT_STATUS = 'PASS'`
6. 실패 시 `EXTRACT_STATUS = 'FAIL'`, `EXTRACT_ERR_MSG`에 오류 내용 저장

## 실행

```bash
mvn spring-boot:run
```

## 추출 실행 API

### 기본 실행

```bash
curl -X POST "http://localhost:8080/api/electronic-documents/extract"
```

### 연도/건수 지정

```bash
curl -X POST "http://localhost:8080/api/electronic-documents/extract?transferYear=2023&limit=100&offset=0"
```

응답 예시:

```json
{
  "transferYear": "2023",
  "requestedLimit": 100,
  "offset": 0,
  "targetCount": 100,
  "successCount": 95,
  "failCount": 5
}
```

## 주의사항

- `CN_ELEC_DOC.SAVE_FILE_NAME`이 있으면 우선 사용하고, 없으면 `ORG_FILE_NAME`을 사용합니다.
- `EXTRACT_ELEC_DOC`에는 `FILE_NAME`, `RC_RFILE_NO`, `RC_RITEM_NO` 유니크 키 기준으로 upsert합니다.
- Apache Tika는 PDF, Word, Excel, PowerPoint 계열은 비교적 안정적으로 처리합니다.
- HWP/HWPX는 파일 버전과 내부 구조에 따라 추출 실패 가능성이 있으므로, 실패 건은 `EXTRACT_ERR_MSG`를 확인해야 합니다.
- 이미지 OCR은 아직 포함하지 않았고, `IMG_DATAS`는 기본값 `[]`로 적재합니다.

## 기존 샘플 CRUD API

기존 테스트용 `records` CRUD API도 남겨두었습니다.

```bash
curl http://localhost:8080/api/records
```
