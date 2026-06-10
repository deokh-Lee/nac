package com.saltlux.nac.chunk;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class RecordContentChunkerTest {

    private final RecordContentChunker chunker = new RecordContentChunker();

    @Test
    void chunksShortFormDocumentWithSubstantiveContent() {
        ChunkSourceDocument source = new ChunkSourceDocument();
        source.setExtractIdx(723L);
        source.setFileName("202311167669_000000000202_S01.zip");
        source.setZipEntryFileName("review.hwp");
        source.setIndexingContents("""
                성별영향분석평가 검토의견 통보서
                관리번호* | 2012A여성013
                정 책 명 | 다문화가족지원법 시행규칙 일부 개정령안
                소관부서 | 기관명 | 여성가족부
                주요 분석평가 내용 | 다문화가족지원센터 전문인력 기준, 위탁계약서, 보수교육의 실시기준, 전문인력양성기관의 지정신청 등에 관한 것으로 성평등에 미치는 영향이 없음
                종합 검토 의견 | 안 제7조는 다문화가족지원사업 추진 시 정책적으로 활용할 수 있도록 교육 관련 인력 현황의 경우 성별로 파악하는 것이 필요합니다.
                검토의견 반영 결과 제출 : 법제처 심사 전까지
                성별영향분석평가법 제8조 제2항의 규정에 따라 성별영향분석평가에 대한 검토의견을 통보합니다.
                <붙임>
                개 정 안 | 검 토 의 견
                수정안 | 검토사유
                제7조(전문인력 양성기관의 지정 신청 등) | 교육 관련 성별 인력 현황을 포함하도록 수정 필요
                """);

        List<RecordContentChunk> chunks = chunker.chunk(source, new ChunkOptions(3_500, 350));

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).getChunkStatus()).isEqualTo("READY");
        assertThat(chunks.get(0).getChunkText()).contains("종합 검토 의견", "검토의견을 통보합니다");
    }

    @Test
    void stillSkipsTinyCoverOnlyText() {
        ChunkSourceDocument source = new ChunkSourceDocument();
        source.setExtractIdx(1L);
        source.setFileName("cover.hwp");
        source.setIndexingContents("""
                보고서
                2023
                여성가족부
                """);

        List<RecordContentChunk> chunks = chunker.chunk(source, new ChunkOptions(3_500, 350));

        assertThat(chunks).isEmpty();
    }
}
