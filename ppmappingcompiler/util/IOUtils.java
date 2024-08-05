package ppmappingcompiler.util;

import com.google.gson.*;
import org.apache.commons.io.FilenameUtils;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("unused")
public class IOUtils {

    // reads internal text resource
    public static String readTextResource(String filePath) throws IOException {
        InputStream is = Utils.class.getClassLoader().getResourceAsStream(filePath);
        if (is == null) throw new FileNotFoundException("Cannot find resource in classpath: " + filePath);
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        return readLines(br);
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean isFile(String filePath) {
        File f = new File(filePath);
        return f.exists() && !f.isDirectory();
    }

    public static String addSuffixToFilePath(String filePath, String suffix) {
        return FilenameUtils.removeExtension(filePath)
                .concat(suffix + ".")
                .concat(FilenameUtils.getExtension(filePath));
    }

    public static String addIncrementalSuffixToFilePath(String filePath, String suffixPattern) {
        if (!isFile(filePath)) return filePath;
        for (int counter = 1; ; counter++) {
            String suffix = String.format(suffixPattern, counter);
            String newFilePath = addSuffixToFilePath(filePath, suffix);
            if (!isFile(newFilePath)) return newFilePath;
        }
    }

    // reads external file
    public static String readFile(String filePath) throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(filePath));
        return readLines(br);
    }

    private static String readLines(BufferedReader br) throws IOException {
        List<String> lines = new ArrayList<>();
        for (String line = br.readLine(); line != null; line = br.readLine()) {
            lines.add(line);
        }
        return String.join("\n", lines);
    }

    public static void writeFile(String filePath, String outputString) throws IOException {
        FileWriter fw = new FileWriter(filePath);
        fw.write(outputString);
        fw.close();
    }

    public static void writeLine(String filePath, String outputString) throws IOException {
        FileWriter fw = new FileWriter(filePath, true);
        fw.append("\n").append(outputString);
        fw.close();
    }

    public static void prettySerializeJson(String jsonString, String filePath) throws IOException {
        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        String prettyJsonString = gson.toJson(JsonParser.parseString(jsonString));
        writeFile(filePath, prettyJsonString);
    }

    public static List<String> loadJsonArrayAsStringList(String filePath) throws IOException {
        String jsonString = IOUtils.readFile(filePath);
        JsonArray jsonArray = (JsonArray) JsonParser.parseString(jsonString);
        List<String> outputList = new ArrayList<>();
        for (JsonElement e : jsonArray) {
            outputList.add(e.getAsString());
        }
        return outputList;
    }

    public static String getRelativePath(String absolutePath) {
        String currentPath = System.getProperty("user.dir");
        return new File(currentPath).toURI().relativize(new File(absolutePath).toURI()).getPath();
    }

}
