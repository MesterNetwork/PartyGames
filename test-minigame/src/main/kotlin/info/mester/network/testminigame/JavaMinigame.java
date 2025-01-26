package info.mester.network.testminigame;

import info.mester.network.partygames.api.Game;
import info.mester.network.partygames.api.Minigame;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class JavaMinigame extends Minigame {
    public JavaMinigame(@NotNull Game game) {
        super(game, "java");
    }

    // to enable players damaging entities, you need to uncancel prePlayerAttackEntity
    @Override
    public void handlePrePlayerAttack(@NotNull PrePlayerAttackEntityEvent event) {
        event.setCancelled(false);
    }

    @Override
    public void handlePlayerDeath(@NotNull PlayerDeathEvent event) {
        event.setCancelled(true);

        Player player = event.getEntity();
        player.setGameMode(GameMode.SPECTATOR);
        @SuppressWarnings("UnstableApiUsage")
        Entity killer = event.getDamageSource().getCausingEntity();
        if (killer instanceof Player killerPlayer) {
            // only award points to players
            killerPlayer.sendMessage(Component.text("You killed " + player.getName() + "!"));
            getGame().addScore(killerPlayer, 10, "Killed " + player.getName());
        }

        // get every online player in the game in survival mode to look for a last player standing
        var remainingPlayer = getOnlinePlayers().stream().filter(Objects::nonNull).filter(p -> p.getGameMode() == GameMode.SURVIVAL).count();
        if (remainingPlayer <= 1) {
            end();
        }
    }


    @Override
    public void start() {
        super.start();

        // everything works as expected
        getAudience().sendMessage(Component.text("Hello from Java!"));

        for (var player : getOnlinePlayers()) {
            assert player != null; // this is very silly because getOnlinePlayers() never returns null elements, but Java is gonna do Java stuff
            player.getInventory().addItem(ItemStack.of(Material.DIAMOND_SWORD));
            player.sendMessage(Component.text("Hello from Java!"));
        }

        // create a countdown without the bar on top of the screen
        startCountdown(20 * 20, false, this::end);
    }

    @Override
    public @NotNull Component getName() {
        return Component.text("Java Minigame");
    }

    @Override
    public @NotNull Component getDescription() {
        return Component.text("This is a minigame written in Java to show interoperability with the API written in Kotlin!");
    }
}
