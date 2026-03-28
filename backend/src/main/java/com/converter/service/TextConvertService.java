package com.converter.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class TextConvertService {

    private final ObjectMapper jsonMapper;
    private final Parser markdownParser;
    private final HtmlRenderer htmlRenderer;
    private final Yaml yaml;

    public TextConvertService() {
        jsonMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        markdownParser = Parser.builder().build();
        htmlRenderer = HtmlRenderer.builder().build();
        DumperOptions opts = new DumperOptions();
        opts.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        opts.setPrettyFlow(true);
        yaml = new Yaml(opts);
    }

    public String convert(String type, String input) throws Exception {
        return switch (type) {
            case "csv-json"  -> csvToJson(input);
            case "json-csv"  -> jsonToCsv(input);
            case "md-html"   -> mdToHtml(input);
            case "json-yaml" -> jsonToYaml(input);
            case "yaml-json" -> yamlToJson(input);
            case "json-xml"  -> jsonToXml(input);
            case "xml-json"  -> xmlToJson(input);
            case "json-toml" -> jsonToToml(input);
            case "toml-json" -> tomlToJson(input);
            default -> throw new IllegalArgumentException("Unknown conversion type: " + type);
        };
    }

    // ── CSV ↔ JSON ────────────────────────────────────────────────────────────

    private String csvToJson(String csv) throws Exception {
        String[] lines = csv.trim().split("\\r?\\n");
        if (lines.length == 0) throw new IllegalArgumentException("Empty CSV");
        String[] headers = parseCsvRow(lines[0]);
        List<Map<String, String>> result = new ArrayList<>();
        for (int i = 1; i < lines.length; i++) {
            if (lines[i].trim().isEmpty()) continue;
            String[] vals = parseCsvRow(lines[i]);
            Map<String, String> row = new LinkedHashMap<>();
            for (int j = 0; j < headers.length; j++) {
                row.put(headers[j], j < vals.length ? vals[j] : "");
            }
            result.add(row);
        }
        return jsonMapper.writeValueAsString(result);
    }

    private String[] parseCsvRow(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuote = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuote && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"'); i++;
                } else {
                    inQuote = !inQuote;
                }
            } else if (c == ',' && !inQuote) {
                fields.add(cur.toString()); cur = new StringBuilder();
            } else {
                cur.append(c);
            }
        }
        fields.add(cur.toString());
        return fields.toArray(new String[0]);
    }

    private String jsonToCsv(String jsonStr) throws Exception {
        List<Map<String, Object>> data = jsonMapper.readValue(jsonStr, new TypeReference<>() {});
        if (data.isEmpty()) return "";
        Set<String> headers = new LinkedHashSet<>();
        data.forEach(row -> headers.addAll(row.keySet()));
        StringBuilder sb = new StringBuilder();
        sb.append(headers.stream().map(this::csvEscape).collect(Collectors.joining(",")));
        for (Map<String, Object> row : data) {
            sb.append('\n');
            sb.append(headers.stream()
                    .map(h -> csvEscape(row.getOrDefault(h, "").toString()))
                    .collect(Collectors.joining(",")));
        }
        return sb.toString();
    }

    private String csvEscape(String val) {
        if (val.contains(",") || val.contains("\"") || val.contains("\n")) {
            return "\"" + val.replace("\"", "\"\"") + "\"";
        }
        return val;
    }

    // ── Markdown → HTML ───────────────────────────────────────────────────────

    private String mdToHtml(String md) {
        Node document = markdownParser.parse(md);
        return htmlRenderer.render(document);
    }

    // ── JSON ↔ YAML ───────────────────────────────────────────────────────────

    private String jsonToYaml(String jsonStr) throws Exception {
        Object obj = jsonMapper.readValue(jsonStr, Object.class);
        return yaml.dump(obj);
    }

    private String yamlToJson(String yamlStr) throws Exception {
        Object obj = yaml.load(yamlStr);
        return jsonMapper.writeValueAsString(obj);
    }

    // ── JSON ↔ XML ────────────────────────────────────────────────────────────

    private String jsonToXml(String jsonStr) throws Exception {
        Object obj = jsonMapper.readValue(jsonStr, Object.class);
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + buildXml(obj, "root", "");
    }

    private String buildXml(Object val, String tag, String indent) {
        String next = indent + "  ";
        if (val == null) return indent + "<" + tag + "/>";
        if (val instanceof List<?> list) {
            return list.stream()
                    .map(item -> buildXml(item, tag, indent))
                    .collect(Collectors.joining("\n"));
        }
        if (val instanceof Map<?, ?> map) {
            StringBuilder sb = new StringBuilder();
            sb.append(indent).append("<").append(tag).append(">\n");
            for (Map.Entry<?, ?> e : map.entrySet()) {
                sb.append(buildXml(e.getValue(), e.getKey().toString(), next)).append("\n");
            }
            sb.append(indent).append("</").append(tag).append(">");
            return sb.toString();
        }
        String escaped = val.toString()
                .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        return indent + "<" + tag + ">" + escaped + "</" + tag + ">";
    }

    private String xmlToJson(String xmlStr) throws Exception {
        DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        Document doc = db.parse(new InputSource(new StringReader(xmlStr)));
        doc.getDocumentElement().normalize();
        Object result = domNodeToObject(doc.getDocumentElement());
        return jsonMapper.writeValueAsString(result);
    }

    @SuppressWarnings("unchecked")
    private Object domNodeToObject(org.w3c.dom.Node node) {
        NodeList children = node.getChildNodes();
        Map<String, Object> obj = new LinkedHashMap<>();
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node child = children.item(i);
            if (child.getNodeType() == org.w3c.dom.Node.TEXT_NODE) {
                String text = child.getNodeValue().trim();
                if (!text.isEmpty()) obj.put("#text", text);
            } else if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                String key = child.getLocalName();
                Object childVal = domNodeToObject(child);
                if (obj.containsKey(key)) {
                    Object existing = obj.get(key);
                    if (existing instanceof List) {
                        ((List<Object>) existing).add(childVal);
                    } else {
                        obj.put(key, new ArrayList<>(Arrays.asList(existing, childVal)));
                    }
                } else {
                    obj.put(key, childVal);
                }
            }
        }
        if (obj.size() == 1 && obj.containsKey("#text")) return obj.get("#text");
        if (obj.isEmpty()) return null;
        return obj;
    }

    // ── JSON ↔ TOML ───────────────────────────────────────────────────────────

    private String jsonToToml(String jsonStr) throws Exception {
        Map<String, Object> obj = jsonMapper.readValue(jsonStr, new TypeReference<>() {});
        if (obj == null) throw new IllegalArgumentException("TOML root must be an object");
        return serializeToml(obj, "").trim();
    }

    @SuppressWarnings("unchecked")
    private String serializeToml(Map<String, Object> obj, String prefix) {
        StringBuilder scalars = new StringBuilder();
        StringBuilder tables = new StringBuilder();
        for (Map.Entry<String, Object> entry : obj.entrySet()) {
            String key = entry.getKey();
            Object val = entry.getValue();
            String fullKey = prefix.isEmpty() ? key : prefix + "." + key;
            if (val instanceof Map) {
                tables.append("\n[").append(fullKey).append("]\n");
                tables.append(serializeToml((Map<String, Object>) val, fullKey));
            } else if (val instanceof List<?> list) {
                if (!list.isEmpty() && list.get(0) instanceof Map) {
                    for (Object item : list) {
                        tables.append("\n[[").append(fullKey).append("]]\n");
                        tables.append(serializeToml((Map<String, Object>) item, fullKey));
                    }
                } else {
                    scalars.append(key).append(" = [")
                            .append(list.stream().map(v -> tomlScalar(v)).collect(Collectors.joining(", ")))
                            .append("]\n");
                }
            } else {
                scalars.append(key).append(" = ").append(tomlScalar(val)).append("\n");
            }
        }
        return scalars.toString() + tables.toString();
    }

    private String tomlScalar(Object v) {
        if (v == null) return "\"\"";
        if (v instanceof Boolean) return v.toString();
        if (v instanceof Number) return v.toString();
        if (v instanceof String s) {
            return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
        }
        return "\"" + v + "\"";
    }

    private String tomlToJson(String toml) throws Exception {
        Map<String, Object> parsed = parseToml(toml);
        return jsonMapper.writeValueAsString(parsed);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseToml(String toml) {
        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> current = root;
        Pattern arrTablePat = Pattern.compile("^\\[\\[(.+?)\\]\\]$");
        Pattern tablePat    = Pattern.compile("^\\[(.+?)\\]$");

        for (String rawLine : toml.split("\\r?\\n")) {
            String line = stripTomlComment(rawLine).trim();
            if (line.isEmpty()) continue;

            Matcher arrM = arrTablePat.matcher(line);
            if (arrM.matches()) {
                String[] keys = arrM.group(1).trim().split("\\.");
                Map<String, Object> parent = root;
                for (int i = 0; i < keys.length - 1; i++) {
                    parent = (Map<String, Object>) parent.computeIfAbsent(keys[i], k -> new LinkedHashMap<>());
                }
                String last = keys[keys.length - 1];
                parent.computeIfAbsent(last, k -> new ArrayList<>());
                Map<String, Object> newItem = new LinkedHashMap<>();
                ((List<Map<String, Object>>) parent.get(last)).add(newItem);
                current = newItem;
                continue;
            }

            Matcher tableM = tablePat.matcher(line);
            if (tableM.matches()) {
                String[] keys = tableM.group(1).trim().split("\\.");
                current = root;
                for (String key : keys) {
                    current = (Map<String, Object>) current.computeIfAbsent(key, k -> new LinkedHashMap<>());
                }
                continue;
            }

            int eqIdx = line.indexOf('=');
            if (eqIdx > 0) {
                String key = line.substring(0, eqIdx).trim();
                String rawVal = line.substring(eqIdx + 1).trim();
                current.put(key, parseTomlValue(rawVal));
            }
        }
        return root;
    }

    private String stripTomlComment(String line) {
        boolean inSq = false, inDq = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if      (c == '\'' && !inDq) inSq = !inSq;
            else if (c == '"'  && !inSq) inDq = !inDq;
            else if (c == '#'  && !inSq && !inDq) return line.substring(0, i);
        }
        return line;
    }

    private Object parseTomlValue(String raw) {
        if ("true".equals(raw))  return true;
        if ("false".equals(raw)) return false;
        if (raw.matches("-?\\d+\\.\\d+([eE][+-]?\\d+)?")) return Double.parseDouble(raw);
        if (raw.matches("-?\\d+")) return Long.parseLong(raw);
        if (raw.startsWith("\"") && raw.endsWith("\"") && raw.length() >= 2) {
            return raw.substring(1, raw.length() - 1)
                    .replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\");
        }
        if (raw.startsWith("'") && raw.endsWith("'") && raw.length() >= 2) {
            return raw.substring(1, raw.length() - 1);
        }
        if (raw.startsWith("[") && raw.endsWith("]")) {
            return parseTomlInlineArray(raw);
        }
        return raw;
    }

    private List<Object> parseTomlInlineArray(String raw) {
        List<Object> result = new ArrayList<>();
        String inner = raw.substring(1, raw.length() - 1).trim();
        if (inner.isEmpty()) return result;
        List<String> parts = new ArrayList<>();
        int depth = 0; boolean inDq = false, inSq = false; int start = 0;
        for (int i = 0; i < inner.length(); i++) {
            char c = inner.charAt(i);
            if      (c == '"' && !inSq) inDq = !inDq;
            else if (c == '\'' && !inDq) inSq = !inSq;
            else if (!inDq && !inSq) {
                if      (c == '[') depth++;
                else if (c == ']') depth--;
                else if (c == ',' && depth == 0) {
                    parts.add(inner.substring(start, i).trim());
                    start = i + 1;
                }
            }
        }
        String tail = inner.substring(start).trim();
        if (!tail.isEmpty()) parts.add(tail);
        parts.forEach(p -> result.add(parseTomlValue(p)));
        return result;
    }
}
