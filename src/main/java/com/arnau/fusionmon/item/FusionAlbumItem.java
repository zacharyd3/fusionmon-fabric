package com.arnau.fusionmon.item;

import com.arnau.fusionmon.fusion.FusionDiscovery;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * El Fusion Album como objeto: se usa como la Pokédex de Cobblemon (clic derecho) y abre las fusiones descubiertas del
 * jugador (FusionAlbumScreen). Se fabrica con una Pokédex de cualquier color rodeada de inyectores.
 *
 * Lo que sale en él lo decide el servidor (FusionDiscovery, guardado con el jugador): el objeto solo lo abre, así que
 * perderlo o tener varios no cambia nada. Sigue valiendo /fusionalbum y Shift + clic derecho con el inyector.
 */
public class FusionAlbumItem extends Item {

    public FusionAlbumItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            FusionDiscovery.open(serverPlayer);
            // El "clic" de abrir un aparato, como un libro
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN,
                    SoundSource.PLAYERS, 0.8F, 1.2F);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.fusionmon.fusion_album.desc").withStyle(ChatFormatting.GRAY));
    }
}
