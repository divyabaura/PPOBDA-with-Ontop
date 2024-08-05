package ppmappingcompiler.fol;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A formula which is not atomic. Its subclasses are:
 * <ul>
 *  <li> a {@link ManyFormulasContainer} ({@link Conjunction} or {@link Disjunction})
 *  <li> a {@link SingleFormulaContainer} ({@link Negation} or {@link Quantifier})
 * </ul>
 */
public abstract class FormulaContainer extends Formula {

    public enum RecursionMethod {BFS, DFS}

    public abstract void replace(Function<Formula, Formula> mapper, RecursionMethod recursionMethod);

    public abstract void apply(Consumer<? super Formula> action, RecursionMethod recursionMethod);

}
