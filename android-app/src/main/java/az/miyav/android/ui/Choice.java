package az.miyav.android.ui;

import com.google.android.material.textfield.MaterialAutoCompleteTextView;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Binds an exposed dropdown to a fixed list of values and remembers the selection. */
final class Choice<T> {
    private final MaterialAutoCompleteTextView view;
    private final List<T> values;
    private final List<String> labels = new ArrayList<>();
    private T selected;
    private Runnable onChange;

    Choice(MaterialAutoCompleteTextView view, List<T> values, Function<T, String> label, T initial) {
        this.view = view;
        this.values = new ArrayList<>(values);
        for (T value : values) {
            labels.add(label.apply(value));
        }
        view.setSimpleItems(labels.toArray(new String[0]));
        view.setOnItemClickListener((parent, item, position, id) -> {
            selected = this.values.get(position);
            if (onChange != null) {
                onChange.run();
            }
        });
        select(initial);
    }

    void select(T value) {
        selected = value;
        int index = values.indexOf(value);
        view.setText(index >= 0 ? labels.get(index) : "", false);
    }

    T value() {
        return selected;
    }

    void onChange(Runnable onChange) {
        this.onChange = onChange;
    }
}
