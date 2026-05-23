# NAC Spring Boot MariaDB Sample

Java Spring Boot에서 MariaDB를 연결해서 사용하는 기본 샘플 프로젝트입니다.

## 기술 스택

- Java 17
- Spring Boot 3.3.5
- Spring Data JPA
- MariaDB Java Client
- Maven

## 실행 전 준비

MariaDB에 데이터베이스를 생성합니다.

```sql
CREATE DATABASE nac_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'nac_user'@'%' IDENTIFIED BY 'nac_password';
GRANT ALL PRIVILEGES ON nac_db.* TO 'nac_user'@'%';
FLUSH PRIVILEGES;
```

## 환경 변수

기본값은 `src/main/resources/application.yml`에 정의되어 있습니다.

```bash
export DB_HOST=localhost
export DB_PORT=3306
export DB_NAME=nac_db
export DB_USERNAME=nac_user
export DB_PASSWORD=nac_password
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
  -d '{"title":"테스트 기록물","description":"MariaDB 연결 테스트"}'
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
