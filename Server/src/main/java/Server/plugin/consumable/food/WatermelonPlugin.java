package plugin.consumable.food;

import plugin.consumable.ConsumableProperties;
import plugin.consumable.Consumables;
import plugin.consumable.Food;
import core.game.node.entity.player.Player;
import core.game.node.item.Item;
import core.plugin.InitializablePlugin;
import core.plugin.Plugin;

/**
 * Represents the watermelon food.
 * @author 'Vexia
 * @date 23/12/2013
 */
@InitializablePlugin
public class WatermelonPlugin extends Food {

	/**
	 * Constructs a new {@code WatermelonPlugin} {@code Object}.
	 */
	public WatermelonPlugin() {
		/**
		 * empty.
		 */
	}

	@Override
	public Plugin<Object> newInstance(Object arg) throws Throwable {
		Consumables.add(new WatermelonPlugin(5982));
		Consumables.add(new WatermelonPlugin(5984));
		return this;
	}

	/**
	 * Constructs a new {@code Watermlon} {@code Object}.
	 * @param item the item.
	 */
	public WatermelonPlugin(int item) {
		super(item, new ConsumableProperties(2));
	}

	@Override
	public void consume(final Item item, final Player player) {
		if (getItem().getId() == 5982) {
			player.getPacketDispatch().sendMessage("Try using a knife to slice it into pieces.");
			return;
		}
		consume(item, player, (int) (player.getSkills().getLifepoints() * 0.05));
	}

}
