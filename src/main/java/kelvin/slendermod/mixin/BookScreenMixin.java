package kelvin.slendermod.mixin;

import kelvin.slendermod.util.NoteScreen;
import net.minecraft.client.gui.screen.ingame.BookScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.PageTurnWidget;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(BookScreen.class)
public class BookScreenMixin implements NoteScreen {

    private boolean isNote = false;

    @Redirect(method = "addPageButtons", at = @At(value = "NEW", target = "(IIZLnet/minecraft/client/gui/widget/ButtonWidget$PressAction;Z)Lnet/minecraft/client/gui/widget/PageTurnWidget;"))
    private PageTurnWidget addPageButtons(int x, int y, boolean isNextPageButton, ButtonWidget.PressAction action, boolean playPageTurnSound) {
        PageTurnWidget widget = new PageTurnWidget(x, y, isNextPageButton, action, playPageTurnSound);
        if (this.isNote) {
            ((NoteScreen) widget).isNote();
        }
        return widget;
    }

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShaderTexture(ILnet/minecraft/util/Identifier;)V"))
    private void render(int texture, Identifier id) {
        this.renderAsNote(this.isNote, id);
    }

    @Override
    public void isNote() {
        this.isNote = true;
    }
}