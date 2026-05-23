package com.saltlux.nac.elecdoc;

import java.util.Locale;

public final class FileTypeUtils {

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

    public static String fileTypeOf(String fileName) {
        String extension = extensionOf(fileName);
        return switch (extension) {
            case "hwp", "hwpx" -> "HWP";
            case "pdf" -> "PDF";
            case "doc", "docx" -> "WORD";
            case "xls", "xlsx" -> "EXCEL";
            case "ppt", "pptx" -> "POWERPOINT";
            case "txt" -> "TEXT";
            default -> extension.isBlank() ? "UNKNOWN" : extension.toUpperCase(Locale.ROOT);
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
