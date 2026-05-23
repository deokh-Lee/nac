# NAC Spring Boot MyBatis MariaDB Sample

Java Spring Boot에서 MyBatis로 MariaDB를 연결해서 사용하는 기본 샘플 프로젝트입니다.

## 기술 스택

- Java 17
- Spring Boot 3.3.5
- MyBatis Spring Boot Starter
- MariaDB Java Client
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

## MyBatis 설정

Mapper XML 위치는 다음과 같습니다.

```yaml
mybatis:
  mapper-locations: classpath:/mapper/**/*.xml
  type-aliases-package: com.saltlux.nac.record
  configuration:
    map-underscore-to-camel-case: true
```

## 예제 테이블

샘플 CRUD API는 `records` 테이블을 사용합니다.

```sql
CREATE TABLE records (
    id BIGINT NOT NULL AUTO_INCREMENT,
    title VARCHAR(300) NOT NULL,
    description TEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

## 실행

```bash
mvn spring-boot:run
```

## API 예시

### 등록

```bash
curl -X POST http://localhost:8080/api/records \
  -H "Content-Type: application/json" \
  -d '{"title":"테스트 기록물","description":"MyBatis MariaDB 연결 테스트"}'
```

### 전체 조회

```bash
curl http://localhost:8080/api/records
```

### 단건 조회

```bash
curl http://localhost:8080/api/records/1
```

### 수정

```bash
curl -X PUT http://localhost:8080/api/records/1 \
  -H "Content-Type: application/json" \
  -d '{"title":"수정된 기록물","description":"수정 테스트"}'
```

### 삭제

```bash
curl -X DELETE http://localhost:8080/api/records/1
```
