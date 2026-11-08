package me.smpcore.gui;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import me.smpcore.util.Theme;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.entity.Player;

/** Thin wrapper over Paper's Dialog API (1.21.7+): a button list and a slider dialog. */
public final class Dialogs {
    private Dialogs() {}

    /** One button. Label and tip are MiniMessage. */
    public record Opt(String label, String tip, Runnable run) {}

    /** Navigation buttons: always red, always with a symbol. */
    public static String prev() { return Theme.red("← Previous"); }

    public static String next() { return Theme.red("Next →"); }

    public static String exitLabel(boolean hasBack) { return Theme.red(hasBack ? "✖ Back" : "✖ Close"); }

    private static final ClickCallback.Options CB = ClickCallback.Options.builder()
            .uses(ClickCallback.UNLIMITED_USES).lifetime(Duration.ofMinutes(15)).build();

    private static ActionButton button(String label, String tip, Runnable run) {
        return ActionButton.create(Theme.parse(label), tip == null ? null : Theme.parse(tip), 150,
                run == null ? null : DialogAction.customClick((view, audience) -> run.run(), CB));
    }

    private static List<DialogBody> bodyOf(List<String> lines) {
        List<DialogBody> out = new ArrayList<>();
        for (String l : lines) out.add(DialogBody.plainMessage(Theme.parse(l)));
        return out;
    }

    /** Grid of buttons. {@code back} may be null (the exit button then just closes). */
    public static void list(Player p, String title, List<String> body, List<Opt> opts, int columns, Runnable back) {
        List<ActionButton> buttons = new ArrayList<>();
        for (Opt o : opts) buttons.add(button(o.label(), o.tip(), o.run()));
        DialogBase base = DialogBase.builder(Theme.title(title)).canCloseWithEscape(true).body(bodyOf(body)).build();
        ActionButton exit = button(exitLabel(back != null), null, back);
        p.showDialog(Dialog.create(f -> f.empty().base(base).type(DialogType.multiAction(buttons, exit, columns))));
    }

    /** A slider plus Save, extra buttons and Back. */
    public static void slider(Player p, String title, List<String> body, String label, float min, float max,
                              float step, float initial, Consumer<Float> save, List<Opt> extra, Runnable back) {
        DialogInput input = DialogInput.numberRange("v", Theme.parse(label), min, max)
                .step(step).initial(initial).width(300).build();
        DialogBase base = DialogBase.builder(Theme.title(title)).canCloseWithEscape(true)
                .body(bodyOf(body)).inputs(List.of(input)).build();
        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(ActionButton.create(Theme.parse(Theme.green("Save")), null, 150,
                DialogAction.customClick((view, audience) -> {
                    Float v = view.getFloat("v");
                    if (v != null) save.accept(v);
                }, CB)));
        for (Opt o : extra) buttons.add(button(o.label(), o.tip(), o.run()));
        ActionButton exit = button(exitLabel(back != null), null, back);
        p.showDialog(Dialog.create(f -> f.empty().base(base).type(DialogType.multiAction(buttons, exit, 2))));
    }

    /** Like {@link #list} but with a search box and a Search button (the callback gets the typed text). */
    public static void search(Player p, String title, List<String> body, String query, Consumer<String> onSearch,
                              List<Opt> opts, int columns, Runnable back) {
        DialogInput input = DialogInput.text("q", Theme.parse("Search")).initial(query == null ? "" : query)
                .maxLength(32).width(300).build();
        DialogBase base = DialogBase.builder(Theme.title(title)).canCloseWithEscape(true)
                .body(bodyOf(body)).inputs(List.of(input)).build();
        List<ActionButton> buttons = new ArrayList<>();
        for (Opt o : opts) buttons.add(button(o.label(), o.tip(), o.run()));
        buttons.add(ActionButton.create(Theme.parse(Theme.blue("Search")), null, 150,
                DialogAction.customClick((view, audience) -> onSearch.accept(view.getText("q")), CB)));
        ActionButton exit = button(exitLabel(back != null), null, back);
        p.showDialog(Dialog.create(f -> f.empty().base(base).type(DialogType.multiAction(buttons, exit, columns))));
    }
}
