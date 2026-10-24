package net.kdt.pojavlaunch.packwiz;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.core.io.ParsingException;
import com.electronwill.nightconfig.toml.TomlParser;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Typed reads from a parsed TOML document, with errors that name the file and the key. */
final class TomlReader {
    private final UnmodifiableConfig config;
    private final String source;

    private TomlReader(UnmodifiableConfig config, String source) {
        this.config = config;
        this.source = source;
    }

    static TomlReader parse(String text, String source) throws PackwizException {
        try {
            return new TomlReader(new TomlParser().parse(new StringReader(text)), source);
        } catch (ParsingException e) {
            throw new PackwizException("Not a valid TOML file: " + e.getMessage(), null, source, e);
        }
    }

    String string(String key, String fallback) throws PackwizException {
        Object value = config.get(key);
        if (value == null) return fallback;
        if (value instanceof String) return (String) value;
        throw new PackwizException("\"" + key + "\" must be text", null, source);
    }

    String requiredString(String key) throws PackwizException {
        String value = string(key, null);
        if (value == null || value.isEmpty()) throw new PackwizException("\"" + key + "\" is missing", null, source);
        return value;
    }

    boolean bool(String key, boolean fallback) throws PackwizException {
        Object value = config.get(key);
        if (value == null) return fallback;
        if (value instanceof Boolean) return (Boolean) value;
        throw new PackwizException("\"" + key + "\" must be true or false", null, source);
    }

    /** A table of text values, e.g. [versions]; empty when the table is missing. */
    Map<String, String> stringTable(String key) throws PackwizException {
        Object value = config.get(key);
        if (value == null) return Collections.emptyMap();
        if (!(value instanceof UnmodifiableConfig)) throw new PackwizException("[" + key + "] must be a table", null, source);
        Map<String, String> table = new LinkedHashMap<>();
        for (UnmodifiableConfig.Entry entry : ((UnmodifiableConfig) value).entrySet()) {
            Object item = entry.getValue();
            if (item instanceof String) table.put(entry.getKey(), (String) item);
        }
        return table;
    }

    /** An array of tables, e.g. [[files]]; empty when missing. */
    List<TomlReader> tables(String key) throws PackwizException {
        Object value = config.get(key);
        if (value == null) return Collections.emptyList();
        if (!(value instanceof List)) throw new PackwizException("[[" + key + "]] must be an array of tables", null, source);
        List<TomlReader> tables = new ArrayList<>();
        for (Object item : (List<?>) value) {
            if (!(item instanceof UnmodifiableConfig)) {
                throw new PackwizException("[[" + key + "]] must be an array of tables", null, source);
            }
            tables.add(new TomlReader((UnmodifiableConfig) item, source));
        }
        return tables;
    }
}
