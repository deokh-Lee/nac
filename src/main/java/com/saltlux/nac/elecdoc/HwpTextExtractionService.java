package com.saltlux.nac.elecdoc;

import java.nio.file.Path;
import kr.dogfoot.hwplib.object.HWPFile;
import kr.dogfoot.hwplib.reader.HWPReader;
import kr.dogfoot.hwplib.tool.textextractor.TextExtractMethod;
import kr.dogfoot.hwplib.tool.textextractor.TextExtractOption;
import kr.dogfoot.hwplib.tool.textextractor.TextExtractor;
import org.springframework.stereotype.Service;

@Service
public class HwpTextExtractionService {

    public String extract(Path path) throws Exception {
        HWPFile hwpFile = HWPReader.fromFile(path.toFile());

        TextExtractOption option = new TextExtractOption();
        option.setMethod(TextExtractMethod.InsertControlTextBetweenParagraphText);
        option.setWithControlChar(false);
        option.setAppendEndingLF(true);

        return TextExtractor.extract(hwpFile, option);
    }
}
