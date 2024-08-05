package ppmappingcompiler.fol;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A formula containing only one subformula.
 */
public abstract class SingleFormulaContainer extends FormulaContainer {

    protected Formula content;

    public Formula getContent() {
        return content.clone();
    }

    @SuppressWarnings("UnusedReturnValue")
    public Formula setContent(Formula f) {
        return content = f.clone();
    }

    public void replaceContent(Function<Formula, Formula> mapper) {
        this.content = mapper.apply(this.content);
    }

    public void apply(Consumer<? super Formula> action) {
        action.accept(content);
    }

    @Override
    public void replace(Function<Formula, Formula> mapper, RecursionMethod recursionMethod) {
        if (recursionMethod == RecursionMethod.DFS && content instanceof FormulaContainer) {
            ((FormulaContainer) content).replace(mapper, recursionMethod);
        }
        replaceContent(mapper);
        if (recursionMethod == RecursionMethod.BFS && content instanceof FormulaContainer) {
            ((FormulaContainer) content).replace(mapper, recursionMethod);
        }
    }

    @Override
    public void apply(Consumer<? super Formula> action, RecursionMethod recursionMethod) {
        if (recursionMethod == RecursionMethod.DFS && content instanceof FormulaContainer) {
            ((FormulaContainer) content).apply(action, recursionMethod);
        }
        apply(action);
        if (recursionMethod == RecursionMethod.BFS && content instanceof FormulaContainer) {
            ((FormulaContainer) content).apply(action, recursionMethod);
        }
    }

    @Override
    public int depth() {
        return this.content.depth() + 1;
    }

    protected void optimizeContent() {
        this.content.optimize();
        this.content = removeUnnecessaryContainer(content);
    }

    @Override
    public SingleFormulaContainer clone() {
        SingleFormulaContainer clone = (SingleFormulaContainer) super.clone();
        clone.content = this.content.clone();
        return clone;
    }

}
