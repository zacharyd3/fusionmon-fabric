package com.arnau.fusionmon.client.screen;

import com.arnau.fusionmon.FusionSafety;
import com.arnau.fusionmon.client.ServerRules;
import com.arnau.fusionmon.fusion.FusionAspects;
import com.arnau.fusionmon.fusion.FusionCalculator;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Gender;
import com.cobblemon.mod.common.pokemon.RenderablePokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Fusion Album: las fusiones que ha descubierto el jugador (FusionDiscovery), de la más reciente a la más antigua, con
 * el aspecto de la Pokédex de Cobblemon. Se abre con el objeto Fusion Album (Pokédex + inyectores), /fusionalbum o
 * Shift + clic derecho con el inyector; la lista la manda el servidor y lo demás se calcula aquí.
 *
 * Mismo esqueleto que la Pokédex (PokedexGUI de Cobblemon, 345 × 207): marco, pantalla y dos paneles de 139 × 163. A la
 * izquierda el buscador y la lista; a la derecha la fusión elegida en 3D sobre la plataforma de su tipo, con un botón
 * para oír su grito, sus tipos, de qué cabeza y cuerpo sale y sus stats base. El marco es el blanco de la Pokédex
 * teñido: rojo a la izquierda y azul a la derecha (cabeza y cuerpo, los colores del inyector), partido por la junta del
 * centro.
 *
 * Solo se usan texturas de Cobblemon que existen igual en 1.7.3 y en 1.8.x (comprobado en los dos jars); el panel de la
 * lista se dibuja a mano porque su textura ya no está en 1.8.
 */
public class FusionAlbumScreen extends Screen {

    // Medidas de la Pokédex de Cobblemon (PokedexGUIConstants)
    private static final int BASE_WIDTH = 345;
    private static final int BASE_HEIGHT = 207;
    private static final int PANEL_WIDTH = 139;
    private static final int PANEL_HEIGHT = 163;
    private static final int BAR = 11;
    private static final int LEFT_X = 26;
    private static final int RIGHT_X = 180;
    private static final int PANEL_Y = 28;
    /** La junta del marco, donde se parte el color. */
    private static final int SEAM_X = 172;

    // Dentro del panel derecho (pokedex_screen_info_overlay)
    private static final int NAME_TAB = 23;
    private static final int PORTRAIT_Y = 12;
    private static final int PORTRAIT_HEIGHT = 82;
    private static final int TYPES_Y = 96;
    private static final int INFO_Y = 107;
    private static final int TOTAL_Y = 152;
    private static final int PLATFORM_WIDTH = 85;
    private static final int PLATFORM_HEIGHT = 22;
    private static final int CRY_WIDTH = 22;
    private static final int CRY_HEIGHT = 10;

    private static final int ROW = 10;

    // Colores de la pantalla de la Pokédex
    private static final int DARK = 0xFF3A96B6;
    private static final int LIGHT = 0xFFEFFDFF;
    private static final int SELECTED = 0xFF5BD1DA;
    private static final int HOVERED = 0xFFD2F4F7;
    private static final int TEXT_DARK = 0xFF1F4E5F;
    private static final int TEXT_MUTED = 0xFF5E8B99;
    private static final int WHITE = 0xFFFFFFFF;

    // Tintes del marco (multiplican el blanco de pokedex_base_white)
    private static final float[] HEAD_TINT = {0.96F, 0.36F, 0.38F};
    private static final float[] BODY_TINT = {0.20F, 0.55F, 1.00F};

    private static final ResourceLocation FRAME = cobblemon("pokedex_base_white");
    private static final ResourceLocation SCREEN = cobblemon("pokedex_screen");
    private static final ResourceLocation INFO = cobblemon("pokedex_screen_info_overlay");
    private static final ResourceLocation CRY_BUTTON = cobblemon("button_sound");
    private static final ResourceLocation SEARCH_ICON = cobblemon("search_icon");

    private static final Stat[] STATS = {Stats.HP, Stats.ATTACK, Stats.DEFENCE, Stats.SPECIAL_ATTACK,
            Stats.SPECIAL_DEFENCE, Stats.SPEED};
    private static final String[] STAT_KEYS = {"hp", "attack", "defence", "special_attack", "special_defence",
            "speed"};

    /**
     * Una fusión descubierta.
     *
     * @param number su número en el álbum (1 = la primera que descubrió)
     * @param search su nombre y los de sus partes, sin tildes y en minúsculas (para el buscador)
     */
    private record Entry(int number, FormData head, FormData body, String name, RenderablePokemon model,
                         String search) {
    }

    private final List<Entry> all = new ArrayList<>();
    private List<Entry> shown = List.of();
    private Entry selected;
    private int scroll;

    private EditBox searchBox;
    private final ModelViewport viewport = new ModelViewport();
    /** Un estado de animación por fusión elegida: al cambiar, la nueva empieza quieta (sin el grito de la anterior). */
    private FloatingState state = new FloatingState();
    private boolean draggingModel;

    /** Esquina del aparato (se calcula en init). */
    private int left;
    private int top;
    private int listRows;

    public FusionAlbumScreen(List<String> discovered) {
        super(Component.translatable("gui.fusionmon.album.title"));
        // Llegan de la más reciente a la más antigua: la más antigua es la n.º 1
        int number = discovered.size();
        for (String key : discovered) {
            Entry entry = entry(number--, key);
            // Una especie que ya no existe (un mod que se ha quitado) no se puede enseñar
            if (entry != null) {
                all.add(entry);
            }
        }
        shown = all;
        selected = all.isEmpty() ? null : all.get(0);
    }

    private static ResourceLocation cobblemon(String name) {
        return ResourceLocation.fromNamespaceAndPath("cobblemon", "textures/gui/pokedex/" + name + ".png");
    }

    @Override
    protected void init() {
        left = (width - BASE_WIDTH) / 2;
        top = (height - BASE_HEIGHT) / 2;
        // Buscador en la barra de arriba del panel izquierdo, sin el marco de Minecraft (la barra ya lo es)
        searchBox = addRenderableWidget(new EditBox(font, left + LEFT_X + 14, top + PANEL_Y + 2, PANEL_WIDTH - 18,
                BAR - 2, Component.translatable("gui.fusionmon.album.search")));
        searchBox.setBordered(false);
        searchBox.setTextColor(WHITE);
        searchBox.setHint(Component.translatable("gui.fusionmon.album.search").withColor(0xFFBFE9F2));
        searchBox.setResponder(this::filter);
        listRows = (PANEL_HEIGHT - 2 * BAR) / ROW;
        clampScroll();
    }

    // ---- Pintado ----

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // El fondo oscuro de Minecraft y luego el aparato; los widgets (el buscador) encima de todo
        renderBackground(graphics, mouseX, mouseY, partialTick);
        renderFrame(graphics);
        renderList(graphics, mouseX, mouseY);
        renderDetail(graphics, mouseX, mouseY, partialTick);
        for (var renderable : renderables) {
            renderable.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    /** Marco teñido (rojo | azul), pantalla y título. */
    private void renderFrame(GuiGraphics graphics) {
        tintedFrame(graphics, left, left + SEAM_X, HEAD_TINT);
        tintedFrame(graphics, left + SEAM_X, left + BASE_WIDTH, BODY_TINT);
        graphics.blit(SCREEN, left, top, 0, 0, BASE_WIDTH, BASE_HEIGHT, BASE_WIDTH, BASE_HEIGHT);
        // En la pestaña oscura de arriba, entre los dos paneles
        graphics.drawCenteredString(font, title, left + BASE_WIDTH / 2, top + 15, WHITE);
    }

    private void tintedFrame(GuiGraphics graphics, int fromX, int toX, float[] tint) {
        graphics.enableScissor(fromX, top, toX, top + BASE_HEIGHT);
        RenderSystem.setShaderColor(tint[0], tint[1], tint[2], 1F);
        graphics.blit(FRAME, left, top, 0, 0, BASE_WIDTH, BASE_HEIGHT, BASE_WIDTH, BASE_HEIGHT);
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        graphics.disableScissor();
    }

    private void renderList(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = left + LEFT_X;
        int y = top + PANEL_Y;
        // Barra del buscador, lista y barra de abajo (como pokedex_screen_search_overlay de 1.7.3)
        graphics.fill(x, y, x + PANEL_WIDTH, y + BAR, DARK);
        graphics.blit(SEARCH_ICON, x + 3, y + 2, 7, 7, 0, 0, 14, 14, 14, 14);
        int listTop = y + BAR;
        graphics.fill(x, listTop, x + PANEL_WIDTH, y + PANEL_HEIGHT - BAR, LIGHT);
        graphics.fill(x, y + PANEL_HEIGHT - BAR, x + PANEL_WIDTH, y + PANEL_HEIGHT, DARK);
        Component count = Component.translatable("gui.fusionmon.album.count", all.size());
        graphics.drawString(font, count, x + 4, y + PANEL_HEIGHT - BAR + 2, WHITE, false);

        if (all.isEmpty()) {
            List<net.minecraft.util.FormattedCharSequence> lines = font.split(
                    Component.translatable("gui.fusionmon.album.empty"), PANEL_WIDTH - 12);
            int lineY = listTop + (listRows * ROW - lines.size() * 10) / 2;
            for (var line : lines) {
                graphics.drawString(font, line, x + (PANEL_WIDTH - font.width(line)) / 2, lineY, TEXT_MUTED, false);
                lineY += 10;
            }
            return;
        }

        int textWidth = PANEL_WIDTH - 8;
        for (int row = 0; row < listRows && scroll + row < shown.size(); row++) {
            Entry entry = shown.get(scroll + row);
            int rowY = listTop + row * ROW;
            boolean hovered = mouseX >= x && mouseX < x + PANEL_WIDTH && mouseY >= rowY && mouseY < rowY + ROW;
            if (entry == selected) {
                graphics.fill(x, rowY, x + PANEL_WIDTH, rowY + ROW, SELECTED);
            } else if (hovered) {
                graphics.fill(x, rowY, x + PANEL_WIDTH, rowY + ROW, HOVERED);
            }
            String number = String.format(Locale.ROOT, "%03d", entry.number);
            graphics.drawString(font, number, x + 3, rowY + 1, TEXT_MUTED, false);
            int nameX = x + 5 + font.width("000");
            Component name = FusionScreenLayout.fit(font, Component.literal(entry.name), textWidth - (nameX - x));
            graphics.drawString(font, name, nameX, rowY + 1, entry == selected ? WHITE : TEXT_DARK, false);
        }
        // Barra de desplazamiento, si no caben todas
        if (shown.size() > listRows) {
            int trackHeight = listRows * ROW;
            int thumb = Math.max(6, trackHeight * listRows / shown.size());
            int thumbY = listTop + (trackHeight - thumb) * scroll / Math.max(1, shown.size() - listRows);
            graphics.fill(x + PANEL_WIDTH - 3, thumbY, x + PANEL_WIDTH - 1, thumbY + thumb, DARK);
        }
    }

    private void renderDetail(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = left + RIGHT_X;
        int y = top + PANEL_Y;
        graphics.blit(INFO, x, y, 0, 0, PANEL_WIDTH, PANEL_HEIGHT, PANEL_WIDTH, PANEL_HEIGHT);
        if (selected == null) {
            return;
        }
        List<ElementalType> types = FusionCalculator.types(selected.head, selected.body);

        // Pestaña oscura: su número; zona clara: el nombre
        graphics.drawCenteredString(font, String.format(Locale.ROOT, "%03d", selected.number), x + NAME_TAB / 2 + 1,
                y + 2, WHITE);
        graphics.drawString(font, FusionScreenLayout.fit(font, Component.literal(selected.name),
                PANEL_WIDTH - NAME_TAB - 6), x + NAME_TAB + 4, y + 2, TEXT_DARK, false);

        // Retrato: la plataforma de su tipo y el modelo encima
        int portraitTop = y + PORTRAIT_Y;
        ResourceLocation platform = cobblemon("platform_base_"
                + (types.isEmpty() ? "normal" : types.get(0).getName().toLowerCase(Locale.ROOT)));
        int platformY = portraitTop + PORTRAIT_HEIGHT - PLATFORM_HEIGHT - 2;
        graphics.blit(platform, x + (PANEL_WIDTH - PLATFORM_WIDTH) / 2, platformY, PLATFORM_WIDTH, PLATFORM_HEIGHT,
                0, 0, 113, 30, 113, 30);
        try {
            viewport.renderIn(graphics, selected.model, state, "", x + 1, portraitTop, PANEL_WIDTH - 2,
                    PORTRAIT_HEIGHT - PLATFORM_HEIGHT / 2, partialTick, null, null, false);
        } catch (RuntimeException e) {
            // Un modelo roto de otro mod no debería cerrar el álbum
            FusionSafety.failed("pintar una fusión en el Fusion Album", e);
        }

        // Botón del grito, arriba a la derecha del retrato
        int cryX = cryX();
        int cryY = cryY();
        // Por delante del modelo: el modelo escribe profundidad y, si se cruza, taparía el botón
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 300);
        graphics.blit(CRY_BUTTON, cryX, cryY, CRY_WIDTH, CRY_HEIGHT, 0, 0, 44, 20, 44, 20);
        if (overCry(mouseX, mouseY)) {
            graphics.fill(cryX, cryY, cryX + CRY_WIDTH, cryY + CRY_HEIGHT, 0x40FFFFFF);
        }
        graphics.pose().popPose();
        if (overCry(mouseX, mouseY)) {
            graphics.renderTooltip(font, Component.translatable("gui.fusionmon.album.cry"), mouseX, mouseY);
        }

        // Barra oscura: los tipos
        graphics.drawCenteredString(font, FusionScreenLayout.fit(font, types(types), PANEL_WIDTH - 6),
                x + PANEL_WIDTH / 2, y + TYPES_Y + 2, WHITE);

        // Zona clara: cabeza + cuerpo y los seis stats base en dos filas
        int infoY = y + INFO_Y + 2;
        Component parts = Component.translatable("gui.fusionmon.album.parts",
                selected.head.getSpecies().getTranslatedName(), selected.body.getSpecies().getTranslatedName());
        graphics.drawCenteredString(font, FusionScreenLayout.fit(font, parts, PANEL_WIDTH - 6), x + PANEL_WIDTH / 2,
                infoY, TEXT_MUTED);
        int total = 0;
        int column = (PANEL_WIDTH - 6) / 3;
        for (int i = 0; i < STATS.length; i++) {
            int value = FusionCalculator.baseStat(selected.head, selected.body, STATS[i],
                    ServerRules.statBonusPercent());
            total += value;
            int statX = x + 3 + (i % 3) * column;
            int statY = infoY + 12 + (i / 3) * 12;
            Component label = Component.translatable("gui.fusionmon.stat." + STAT_KEYS[i]);
            graphics.drawString(font, label, statX, statY, TEXT_MUTED, false);
            String number = String.valueOf(value);
            graphics.drawString(font, number, statX + column - 4 - font.width(number), statY, TEXT_DARK, false);
        }

        // Barra de abajo: el total
        graphics.drawCenteredString(font, Component.translatable("gui.fusionmon.album.total", total),
                x + PANEL_WIDTH / 2, y + TOTAL_Y + 2, WHITE);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Solo el oscurecido, sin el desenfoque del menú (como la Pokédex)
        renderTransparentBackground(graphics);
    }

    // ---- Datos ----

    /** Una fusión a partir de su clave ("cobblemon:charizard/normal>cobblemon:pikachu/normal"); null si no existe. */
    private static Entry entry(int number, String key) {
        String[] parts = key.split(">");
        if (parts.length != 2) {
            return null;
        }
        FormData head = form(parts[0]);
        FormData body = form(parts[1]);
        if (head == null || body == null) {
            return null;
        }
        Set<String> aspects = partAspects(head);
        aspects.addAll(FusionAspects.fusionAspects(body.getSpecies(), partAspects(body)));
        RenderablePokemon model = new RenderablePokemon(head.getSpecies(), aspects, ItemStack.EMPTY);
        String headName = head.getSpecies().getTranslatedName().getString();
        String bodyName = body.getSpecies().getTranslatedName().getString();
        String name = FusionCalculator.name(headName, bodyName);
        return new Entry(number, head, body, name, model, normalize(name + " " + headName + " " + bodyName));
    }

    /** "cobblemon:raichu/alola" → la forma de Alola de Raichu (la normal si ya no la tiene). */
    private static FormData form(String part) {
        int slash = part.lastIndexOf('/');
        ResourceLocation id = ResourceLocation.tryParse(slash < 0 ? part : part.substring(0, slash));
        Species species = id == null ? null : PokemonSpecies.getByIdentifier(id);
        if (species == null) {
            return null;
        }
        String formName = slash < 0 ? "" : part.substring(slash + 1);
        for (FormData form : species.getForms()) {
            if (form.getName().equalsIgnoreCase(formName)) {
                return form;
            }
        }
        return species.getStandardForm();
    }

    /** Los aspects de una parte: los de su forma (regional...) y su sexo más habitual (para el modelo de macho/hembra). */
    private static Set<String> partAspects(FormData form) {
        Set<String> aspects = new HashSet<>(form.getAspects());
        Set<Gender> genders = form.getSpecies().getPossibleGenders();
        if (genders.contains(Gender.MALE)) {
            aspects.add("male");
        } else if (genders.contains(Gender.FEMALE)) {
            aspects.add("female");
        }
        return aspects;
    }

    private void filter(String text) {
        String wanted = normalize(text);
        shown = wanted.isEmpty() ? all : all.stream().filter(entry -> entry.search.contains(wanted)).toList();
        scroll = 0;
        if (!shown.isEmpty() && !shown.contains(selected)) {
            choose(shown.get(0));
        }
    }

    private static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim();
    }

    private static Component types(List<ElementalType> types) {
        List<Component> names = new ArrayList<>();
        for (ElementalType type : types) {
            names.add(type.getDisplayName());
        }
        return FusionScreenLayout.joinTypes(names);
    }

    private void choose(Entry entry) {
        if (entry != selected) {
            selected = entry;
            state = new FloatingState();
        }
    }

    private void select(int index) {
        if (shown.isEmpty()) {
            return;
        }
        index = Mth.clamp(index, 0, shown.size() - 1);
        choose(shown.get(index));
        // Que se vea en la lista
        if (index < scroll) {
            scroll = index;
        } else if (index >= scroll + listRows) {
            scroll = index - listRows + 1;
        }
    }

    private void clampScroll() {
        scroll = Mth.clamp(scroll, 0, Math.max(0, shown.size() - listRows));
    }

    /**
     * El grito, como el botón de la Pokédex: la animación "cry" del modelo, cuyos fotogramas de sonido tocan el grito.
     * En una fusión pasan por FusionCry (BedrockSoundKeyframeMixin): suenan la cabeza y el cuerpo mezclados, y la
     * cabeza pegada también abre la boca (FusionGraft.mirrorAnimation).
     */
    private void playCry() {
        try {
            state.getActiveAnimations().clear();
            state.addFirstAnimation(Set.of("cry"));
        } catch (RuntimeException e) {
            FusionSafety.failed("el grito en el Fusion Album", e);
        }
    }

    // ---- Ratón y teclado ----

    private int cryX() {
        return left + RIGHT_X + PANEL_WIDTH - CRY_WIDTH - 3;
    }

    private int cryY() {
        return top + PANEL_Y + PORTRAIT_Y + 2;
    }

    private boolean overCry(double x, double y) {
        return selected != null && x >= cryX() && x < cryX() + CRY_WIDTH && y >= cryY() && y < cryY() + CRY_HEIGHT;
    }

    private boolean inList(double x, double y) {
        int listTop = top + PANEL_Y + BAR;
        return x >= left + LEFT_X && x < left + LEFT_X + PANEL_WIDTH && y >= listTop && y < listTop + listRows * ROW;
    }

    private boolean inPortrait(double x, double y) {
        int portraitTop = top + PANEL_Y + PORTRAIT_Y;
        return x >= left + RIGHT_X && x < left + RIGHT_X + PANEL_WIDTH && y >= portraitTop
                && y < portraitTop + PORTRAIT_HEIGHT;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (overCry(mouseX, mouseY)) {
            playCry();
            return true;
        }
        if (inList(mouseX, mouseY)) {
            int index = scroll + (int) ((mouseY - (top + PANEL_Y + BAR)) / ROW);
            if (index < shown.size()) {
                choose(shown.get(index));
            }
            return true;
        }
        if (selected != null && inPortrait(mouseX, mouseY)) {
            viewport.press(button);
            draggingModel = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        viewport.release();
        draggingModel = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingModel && viewport.drag(dragX, dragY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (inList(mouseX, mouseY)) {
            scroll -= (int) Math.signum(scrollY) * 3;
            clampScroll();
            return true;
        }
        if (inPortrait(mouseX, mouseY)) {
            viewport.scroll(scrollY);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Flechas: la anterior o la siguiente de la lista (también mientras se escribe en el buscador)
        if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN) {
            int index = shown.indexOf(selected);
            select(index + (keyCode == GLFW.GLFW_KEY_UP ? -1 : 1));
            return true;
        }
        // Espacio fuera del buscador: el grito
        if (keyCode == GLFW.GLFW_KEY_SPACE && !searchBox.isFocused()) {
            playCry();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        // Sin pausa, las animaciones de los modelos siguen
        return false;
    }
}
