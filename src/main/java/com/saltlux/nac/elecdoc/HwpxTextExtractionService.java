package com.saltlux.nac.elecdoc;

import java.nio.file.Path;
import kr.dogfoot.hwpxlib.object.HWPXFile;
import kr.dogfoot.hwpxlib.reader.HWPXReader;
import kr.dogfoot.hwpxlib.tool.textextractor.TextExtractMethod;
import kr.dogfoot.hwpxlib.tool.textextractor.TextExtractor;
import kr.dogfoot.hwpxlib.tool.textextractor.TextMarks;
import org.springframework.stereotype.Service;

@Service
public class HwpxTextExtractionService {

    public String extract(Path path) throws Exception {
        HWPXFile hwpxFile = HWPXReader.fromFile(path.toFile());

        TextMarks textMarks = new TextMarks()
                .paraSeparatorAnd("\n")
                .lineBreakAnd("\n")
                .tabAnd("\t")
                .tableCellSeparatorAnd("\t")
                .tableRowSeparatorAnd("\n");

        return TextExtractor.extract(
                hwpxFile,
                TextExtractMethod.InsertControlTextBetweenParagraphText,
                false,
                textMarks
        );
    }
}
