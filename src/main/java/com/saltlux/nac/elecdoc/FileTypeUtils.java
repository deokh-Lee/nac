package com.saltlux.nac.elecdoc;

import java.util.Locale;

public final class FileTypeUtils {

    private static final String UNKNOWN = "UNKNOWN";

    private FileTypeUtils() {
    }

    public static String extensionOf(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "";
        }

        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return "";
        }

        return fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * FILE_NAME 규칙에서 문서 구분 값을 추출합니다.
     *
     * 예) 202311165978_000000000001_N01.hwp -> N
     * 예) 202311167633_000000000004_S01.hwp -> S
     *
     * N: 시행문, S: 붙임문서
     */
    public static String documentTypeOf(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return UNKNOWN;
        }

        String normalized = fileName.replace("\\", "/");
        int lastSlashIndex = normalized.lastIndexOf('/');
        String onlyFileName = lastSlashIndex >= 0 ? normalized.substring(lastSlashIndex + 1) : normalized;

        int dotIndex = onlyFileName.lastIndexOf('.');
        String baseName = dotIndex > 0 ? onlyFileName.substring(0, dotIndex) : onlyFileName;
        String[] tokens = baseName.split("_");

        for (int i = tokens.length - 1; i >= 0; i--) {
            String token = tokens[i].toUpperCase(Locale.ROOT);
            if (token.length() >= 2
                    && (token.charAt(0) == 'N' || token.charAt(0) == 'S')
                    && Character.isDigit(token.charAt(1))) {
                return String.valueOf(token.charAt(0));
            }
        }

        return UNKNOWN;
    }

    public static String fileTypeOf(String fileName) {
        String extension = extensionOf(fileName);
        return switch (extension) {
            case "hwp", "hwpx" -> "HWP";
            case "pdf" -> "PDF";
            case "doc", "docx" -> "WORD";
            case "xls", "xlsx" -> "EXCEL";
            case "ppt", "pptx" -> "POWERPOINT";
            case "txt" -> "TEXT";
            default -> extension.isBlank() ? UNKNOWN : extension.toUpperCase(Locale.ROOT);
        };
    }

    public static String fileGubunOf(String fileName) {
        String extension = extensionOf(fileName);
        return switch (extension) {
            case "hwp", "hwpx" -> "HWP";
            case "pdf" -> "PDF";
            case "doc", "docx", "xls", "xlsx", "ppt", "pptx" -> "OFFICE";
            case "txt" -> "TEXT";
            default -> "ETC";
        };
    }
}
