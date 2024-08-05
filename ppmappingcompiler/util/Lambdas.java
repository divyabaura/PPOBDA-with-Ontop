package ppmappingcompiler.util;

@SuppressWarnings("unused")
public class Lambdas {

    @FunctionalInterface
    public interface Function3<One, Two, Three> {
        Three apply(One one, Two two);
    }

    @FunctionalInterface
    public interface Function4<One, Two, Three, Four> {
        Four apply(One one, Two two, Three three);
    }

    @FunctionalInterface
    interface Function5<One, Two, Three, Four, Five> {
        Five apply(One one, Two two, Three three, Four four);
    }

    @FunctionalInterface
    interface Function6<One, Two, Three, Four, Five, Six> {
        Six apply(One one, Two two, Three three, Four four, Five five);
    }

}
