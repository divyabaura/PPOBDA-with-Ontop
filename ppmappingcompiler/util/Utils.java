package ppmappingcompiler.util;

import javax.annotation.Nonnull;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collector;
import java.util.stream.Collectors;

@SuppressWarnings("unused")
public class Utils {

    /**
     * This function returns the first non-null value of the input array.
     */
    @SafeVarargs
    public static <T> T coalesce(T... items) {
        for (T i : items) if (i != null) return i;
        return null;
    }

    /**
     * Given a regex "{@code A(B)C|D(E)F}" and a target "{@code DEF}", it will return "{@code E}".
     */
    public static String getFirstMatchingGroup(String regex, String target) {
        Matcher matcher = Pattern.compile(regex).matcher(target);
        if (matcher.matches()) {
            if (matcher.groupCount() == 0) return matcher.group(0);
            for (int j = 1; j <= matcher.groupCount(); j++) {
                String match = matcher.group(j);
                if (match != null) return matcher.group(j);
            }
        }
        return null;
    }

    /**
     * Experimentally, this method is faster than the one with only two arguments and also w.r.t.
     * the multiple calls to {@link String#replaceAll}.
     *
     * @param templateString       A {@link String string} with variables to replace.
     * @param variableToValueMap   A {@link String string}-assignment for all the variables to replace.
     * @param variableRegexPattern The pattern matched by all (and only by) the template's variables.
     * @return A new {@link String string} in which all the indicated variables have been replaced.
     */
    public static String formatTemplate(String templateString, Map<String, String> variableToValueMap, String variableRegexPattern) {
        StringBuilder sb = new StringBuilder();
        Pattern pattern = Pattern.compile("(" + variableRegexPattern + ")(.*)", Pattern.DOTALL);
        for (String s : templateString.split("(?=" + variableRegexPattern + ")")) {
            Matcher m = pattern.matcher(s);
            if (m.matches()) sb
                    .append(variableToValueMap.get(m.group(1)))
                    .append(m.group(2));
            else sb.append(s);
        }
        return sb.toString();
    }

    public static String formatTemplate(String templateString, Map<String, String> variableToValueMap) {
        StringBuilder sb = new StringBuilder();
        String regex = variableToValueMap
                .keySet()
                .stream()
                .map(Pattern::quote)
                .collect(Collectors.joining("|"));
        for (String match : templateString.split(String.format("(?<=%s)|(?=%s)", regex, regex))) {
            sb.append(variableToValueMap.getOrDefault(match, match));
        }
        return sb.toString();
    }

    public static String indent(@Nonnull String s, String indentingString) {
        return s.replaceAll("(?m)^", indentingString);
    }

    public static String indent(@Nonnull String s) {
        return indent(s, "\t");
    }

    public static StringBuilder indent(@Nonnull StringBuilder s) {
        return new StringBuilder(indent(s.toString(), "\t"));
    }

    public static <T> StringBuilder prependSB(StringBuilder sb, T toPrepend) {
        sb.insert(0, toPrepend);
        return sb;
    }

    public static <T> StringBuilder joinSB(String delimiter, Collection<T> collection) {
        if (collection.isEmpty()) return new StringBuilder();

        Iterator<?> it = collection.iterator();
        Object firstElem = it.next();
        StringBuilder result = firstElem instanceof StringBuilder
                ? (StringBuilder) firstElem
                : new StringBuilder(firstElem.toString());
        while (it.hasNext()) {
            result.append(delimiter).append(it.next());
        }
        return result;
    }

    /**
     * @param elements Any {@link Collection collection} of elements.
     * @param k        A positive integer.
     * @param <T>      The type of collection's elements.
     * @return A {@link List list} of lists (of size {@code k}).<br>
     * Each sublist is a combination of {@code elements}, possibly containing repetitions.
     */
    @Nonnull
    public static <T> List<List<T>> combinationsWithRepetitions(@Nonnull Collection<T> elements, int k) {
        int n = elements.size();
        List<List<T>> combinations = new ArrayList<>();

        if (k == 0) {
            List<T> c = new ArrayList<>();
            combinations.add(c);
            return combinations;
        }

        for (T element : elements) {
            for (int i = 0; i < Math.pow(n, (k - 1)); i++) {
                List<T> c = new ArrayList<>();
                c.add(element);
                combinations.add(c);
            }
        }
        if (combinations.isEmpty()) return combinations;

        for (int step = 1; step < k; step++) {
            int index = 0;
            for (int i = 0; i < Math.pow(n, step); i++) {
                for (T element : elements) {
                    for (int j = 0; j < Math.pow(n, (k - 1 - step)); j++) {
                        combinations.get(index++).add(element);
                    }
                }
            }
        }
        return combinations;
    }

    /**
     * This function takes a collection of objects and filters it according to a specific class.<br>
     * It does NO side effect.
     *
     * @param collection The input collection.
     * @param tClass     The class according to which the collection must be filtered.
     * @return A new collection filtered according to the specified class.
     */
    public static <C extends Collection<T>, T> C filterByClass(Collection<?> collection, Class<T> tClass,
                                                               Collector<T, ?, C> collector) {
        return collection.stream()
                .filter(e -> tClass.isAssignableFrom(e.getClass()))
                .map(tClass::cast)
                .collect(collector);
    }

    @SafeVarargs
    @Nonnull
    public static <T> Set<T> setUnion(Collection<T>... sets) {
        Set<T> result = new HashSet<>();
        for (Collection<T> s : sets) result.addAll(s);
        return result;
    }

    @SafeVarargs
    @Nonnull
    public static <T> Set<T> setIntersection(Collection<T>... sets) {
        if (sets.length == 0) return new HashSet<>();
        Set<T> result = new HashSet<>(sets[0]);
        for (Collection<T> s : sets) result.retainAll(s);
        return result;
    }

    @SafeVarargs
    @Nonnull
    public static <T> Set<T> setDifference(Collection<T> positiveSet, Collection<T>... negativeSets) {
        Set<T> result = new HashSet<>(positiveSet);
        for (Collection<T> negativeSet : negativeSets) result.removeAll(negativeSet);
        return result;
    }

    /**
     * This function takes in input a set of sets and merges all the subsets sharing at least one element.
     *
     * @param unmergedSets A set of possibly joinable subsets.
     * @param <T>          The class of the subsets' elements.
     * @return A set of disjoint subsets.
     */
    @Nonnull
    public static <T> Set<Set<T>> mergeIntersectingSets(Collection<? extends Set<T>> unmergedSets) {
        boolean edited = false;

        Set<Set<T>> mergedSets = new HashSet<>();
        for (Set<T> subset1 : unmergedSets) {
            boolean merged = false;

            // if at least one element is contained in another subset, then merge the subsets
            for (Set<T> subset2 : mergedSets) {
                if (!Collections.disjoint(subset1, subset2)) {
                    subset2.addAll(subset1);
                    merged = true;
                    edited = true;
                }
            }
            // otherwise, add the current subset as a new subset
            if (!merged) mergedSets.add(subset1);
        }

        if (edited) return mergeIntersectingSets(mergedSets); // continue merging until reaching a fixpoint
        else return mergedSets;
    }

    /**
     * This functions computes the cartesian product among two sets of lists, concatenating
     * all the strings of the first one with all the strings of the second one.<br>
     * Example
     * <ul>
     * 	<li> Input:  {@code <{"a", "b", "c"}, {"x", "y", "z"}>}
     * 	<li> Output: {@code {"ax", "ay", "az", "bx", "by", "bz", "cx", "cy", "cz"}}
     * </ul>
     *
     * @param set1 the frist {@link Set set} of {@link String strings}.
     * @param set2 the second {@link Set set} of {@link String strings}.
     * @return A new {@link Set set} of {@link String strings} representing the cartesian product of the first two.
     */
    @Nonnull
    public static Set<String> stringCartesianProduct(@Nonnull Set<String> set1, @Nonnull Set<String> set2) {
        Set<String> resultSet = new HashSet<>();
        for (String s1 : set1) {
            for (String s2 : set2) {
                resultSet.add(s1.concat(s2));
            }
        }
        return resultSet;
    }

    public static String getLastURIPart(String uri) throws URISyntaxException {
        String[] segments = new URI(uri).getPath().split("/");
        return segments[segments.length - 1];
    }

    public static String appendSlashIfMissing(String str) {
        if (str == null) return null;
        String slashChar = str.contains("\\") ? "\\" : "/";
        return str.endsWith(slashChar) ? str : str + slashChar;
    }

    public static String prependSlashIfMissing(@Nonnull String str) {
        String slashChar = str.contains("\\") ? "\\" : "/";
        return str.startsWith(slashChar) ? str : slashChar + str;
    }

    public static String getTimestamp(boolean readable) {
        if (readable) return new Date(System.currentTimeMillis()).toString();
        else return new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(System.currentTimeMillis());
    }

    public static String unescape(String sourceString, CharSequence charactersToUnescape) {
        for (String c : ("\\" + charactersToUnescape).split(""))
            sourceString = sourceString.replace("\\" + c, c);
        return sourceString;
    }

    public static String escape(String sourceString, CharSequence charactersToEscape) {
        for (String c : ("\\" + charactersToEscape).split(""))
            sourceString = sourceString.replace(c, "\\" + c);
        return sourceString;
    }

}
