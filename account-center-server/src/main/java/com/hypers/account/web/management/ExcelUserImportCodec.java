package com.hypers.account.web.management;

import com.hypers.account.web.management.UserImportModels.Row;
import com.hypers.account.web.management.UserImportModels.RowError;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipInputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParserFactory;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

@Component
public class ExcelUserImportCodec {
    public static final int MAX_FILE_BYTES = 5 * 1024 * 1024;
    private static final int MAX_ENTRY_BYTES = 16 * 1024 * 1024;
    private static final int MAX_EXPANDED_BYTES = 32 * 1024 * 1024;
    private static final List<String> FIELDS = List.of("account", "email", "name", "phone");

    public byte[] template() {
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("users");
            var textStyle = workbook.createCellStyle();
            textStyle.setDataFormat(workbook.createDataFormat().getFormat("@"));
            var header = sheet.createRow(0);
            for (int i = 0; i < FIELDS.size(); i++) {
                sheet.setDefaultColumnStyle(i, textStyle);
                sheet.setColumnWidth(i, 28 * 256);
                header.createCell(i).setCellValue(FIELDS.get(i));
            }
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("cannot create import template", exception);
        }
    }

    public List<Row> read(byte[] bytes, String filename) {
        if (bytes.length > MAX_FILE_BYTES) throw tooLarge();
        if (bytes.length == 0 || filename == null
                || !filename.toLowerCase(Locale.ROOT).endsWith(".xlsx")) throw invalid();
        validateArchive(bytes);
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            if (workbook.getNumberOfSheets() != 1 || workbook.isMacroEnabled()) throw invalid();
            var sheet = workbook.getSheetAt(0);
            if (sheet.getNumMergedRegions() != 0) throw invalid();
            if (sheet.getLastRowNum() > 1000) throw tooLarge();
            var header = sheet.getRow(0);
            if (header == null || header.getLastCellNum() != 4) throw invalid();
            for (int c = 0; c < 4; c++) {
                if (header.getCell(c) == null || header.getCell(c).getCellType() != CellType.STRING
                        || !FIELDS.get(c).equals(header.getCell(c).getStringCellValue())) throw invalid();
            }
            List<Row> rows = new ArrayList<>();
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                var source = sheet.getRow(r);
                if (source == null) continue;
                boolean populated = false;
                for (var cell : source) {
                    if (cell.getCellType() == CellType.BLANK) continue;
                    if (cell.getCellType() == CellType.STRING && cell.getStringCellValue().isBlank()) continue;
                    if (cell.getColumnIndex() >= 4) throw invalid();
                    populated = true;
                }
                if (!populated) continue;
                List<RowError> errors = new ArrayList<>();
                List<String> values = new ArrayList<>();
                for (int c = 0; c < 4; c++) {
                    var cell = source.getCell(c);
                    String value = "";
                    if (cell != null && cell.getCellType() != CellType.BLANK) {
                        if (cell.getCellType() == CellType.STRING) value = cell.getStringCellValue().trim();
                        else errors.add(new RowError(FIELDS.get(c), "import.text", null));
                    }
                    if (value.length() > 4096) throw tooLarge();
                    values.add(value);
                }
                rows.add(new Row(r + 1, values.get(0), values.get(1), values.get(2), values.get(3), errors));
            }
            if (rows.isEmpty()) throw invalid();
            return rows;
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalid();
        }
    }

    // 在构建 POI 对象图之前限制压缩包和 XML，拒绝外链、实体及超大行号。
    private void validateArchive(byte[] bytes) {
        try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            var names = new HashSet<String>();
            int expanded = 0;
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                String name = entry.getName();
                if (!names.add(name) || names.size() > 256) throw tooLarge();
                if (!entry.isDirectory() && !name.endsWith(".xml") && !name.endsWith(".rels")) throw invalid();
                if (name.contains("..") || name.startsWith("/") || name.contains("\\")
                        || name.toLowerCase(Locale.ROOT).contains("vbaproject")
                        || name.contains("embeddings/")) throw invalid();
                byte[] content = zip.readNBytes(MAX_ENTRY_BYTES + 1);
                expanded += content.length;
                if (content.length > MAX_ENTRY_BYTES || expanded > MAX_EXPANDED_BYTES) throw tooLarge();
                if (name.endsWith(".xml") || name.endsWith(".rels")) {
                    var factory = SAXParserFactory.newInstance();
                    factory.setNamespaceAware(true);
                    factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
                    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                    factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
                    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
                    factory.newSAXParser().parse(new ByteArrayInputStream(content), new DefaultHandler() {
                        private int rows;
                        private int cells;
                        @Override
                        public void startElement(String uri, String local, String qualified, Attributes attrs)
                                throws SAXException {
                            if ("External".equalsIgnoreCase(attrs.getValue("TargetMode"))
                                    || "f".equals(local) || "mergeCell".equals(local)) {
                                throw new SAXException("unsupported workbook content");
                            }
                            if ("c".equals(local)) {
                                String address = attrs.getValue("r");
                                if (++cells > 4004) throw tooLarge();
                                if (address == null || !address.matches("[A-D][1-9][0-9]{0,3}")) {
                                    throw new SAXException("cell limit");
                                }
                            }
                            if ("row".equals(local)) {
                                if (++rows > 1001) throw tooLarge();
                                if (attrs.getValue("r") == null) {
                                    throw new SAXException("row count limit");
                                }
                                try {
                                    int row = Integer.parseInt(attrs.getValue("r"));
                                    if (row > 1001) throw tooLarge();
                                    if (row < 1) throw new SAXException("invalid row");
                                } catch (NumberFormatException exception) {
                                    throw new SAXException("invalid row");
                                }
                            }
                        }
                    });
                }
            }
            if (!names.contains("[Content_Types].xml") || !names.contains("xl/workbook.xml")) throw invalid();
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalid();
        }
    }

    public static ApiException invalid() {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "IMPORT_FILE_INVALID", "import.invalidFile");
    }

    public static ApiException tooLarge() {
        return new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "IMPORT_LIMIT_EXCEEDED", "import.tooLarge");
    }
}
