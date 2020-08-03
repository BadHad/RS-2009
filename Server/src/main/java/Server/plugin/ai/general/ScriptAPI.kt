package plugin.ai.general

import core.cache.def.impl.ItemDefinition
import core.game.interaction.MovementPulse
import core.game.node.Node
import core.game.node.`object`.GameObject
import core.game.node.entity.Entity
import core.game.node.entity.npc.NPC
import core.game.node.entity.player.Player
import core.game.node.entity.player.info.PlayerDetails
import core.game.node.item.GroundItem
import core.game.node.item.GroundItemManager
import core.game.node.item.Item
import core.game.system.SystemLogger
import core.game.system.task.Pulse
import core.game.world.GameWorld
import core.game.world.map.Location
import core.game.world.map.RegionManager
import core.game.world.map.path.Path
import core.game.world.map.path.Pathfinder
import core.game.world.update.flag.context.Animation
import core.game.world.update.flag.context.Graphics
import core.tools.ItemNames
import core.tools.RandomFunction
import plugin.ai.AIPlayer
import plugin.ai.AIRepository
import plugin.ai.general.scriptrepository.LobsterCatcher
import plugin.ai.general.scriptrepository.SeersMagicTrees
import plugin.consumable.Consumable
import plugin.consumable.ConsumableProperties
import plugin.consumable.Consumables
import plugin.consumable.Food
import plugin.ge.GEOfferDispatch
import plugin.ge.GrandExchangeOffer
import plugin.skill.Skills
import java.util.ArrayList
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

class ScriptAPI(private val bot: Player) {
    val GRAPHICSUP = Graphics(1576)
    val ANIMATIONUP = Animation(8939)
    val GRAPHICSDOWN = Graphics(1577)
    val ANIMATIONDOWN = Animation(8941)

    fun distance(n1: Node, n2: Node): Double {
        return sqrt((n1.location.x - n2.location.x.toDouble()).pow(2.0) + (n2.location.y - n1.location.y.toDouble()).pow(2.0))
    }

    fun getNearestNode(entityName: String): Node? {
        var entity: Node? = null
        var minDistance = Double.MAX_VALUE
        for (node in RegionManager.forId(bot.location.regionId).planes[bot.location.z].entities) {
            if (node != null && node.name == entityName && distance(bot, node) < minDistance && !Pathfinder.find(bot, node).isMoveNear) {
                entity = node
                minDistance = distance(bot, node)
            }
        }
        return entity
    }

    fun getNearestNode(id: Int, `object`: Boolean): Node? {
        if (`object`) {
            var entity: Node? = null
            var minDistance = Double.MAX_VALUE
            for (objects in RegionManager.forId(bot.location.regionId).planes[bot.location.z].objects) {
                for(e in objects) {
                    if (e != null && e.id == id && distance(bot, e) < minDistance && !Pathfinder.find(bot, e).isMoveNear && e.isActive) {
                        entity = e
                        minDistance = distance(bot, e)
                    }
                }
            }
            return if(entity == null) null else entity as GameObject
        } else {
            var entity: Node? = null
            var minDistance = Double.MAX_VALUE
            for (e in RegionManager.forId(bot.location.regionId).planes[bot.location.z].entities) {
                if (e != null && e.id == id && distance(bot, e) < minDistance && !Pathfinder.find(bot, e).isMoveNear) {
                    entity = e
                    minDistance = distance(bot, e)
                }
            }
            return entity
        }
    }

    fun getNearestNode(name: String, `object`: Boolean): Node? {
        if (`object`) {
            var entity: Node? = null
            var minDistance = Double.MAX_VALUE
            for (objects in RegionManager.forId(bot.location.regionId).planes[bot.location.z].objects) {
                for(e in objects) {
                    if (e != null && e.name.toLowerCase() == name.toLowerCase() && distance(bot, e) < minDistance && !Pathfinder.find(bot, e).isMoveNear && e.isActive) {
                        entity = e
                        minDistance = distance(bot, e)
                    }
                }
            }
            return if(entity == null) null else entity as GameObject
        } else {
            var entity: Node? = null
            var minDistance = Double.MAX_VALUE
            for (e in RegionManager.forId(bot.location.regionId).planes[bot.location.z].entities) {
                if (e != null && e.name.toLowerCase() == name.toLowerCase() && distance(bot, e) < minDistance && !Pathfinder.find(bot, e).isMoveNear) {
                    entity = e
                    minDistance = distance(bot, e)
                }
            }
            return entity
        }
    }

    private fun getNearestGroundItem(id: Int): GroundItem? {
        var distance = 11.0
        var closest: GroundItem? = null
        if(AIRepository.getItems(bot) == null) return null
        for(item in AIRepository.getItems(bot)!!.filter { it.distance(bot.location) < 10 }){
            if(item.id == id){
                //distance = item.distance(bot.location)
                closest = item
            }
        }
        if(!GroundItemManager.getItems().contains(closest)) AIRepository.getItems(bot)?.remove(closest).also {return null}
        return closest
    }

    fun takeNearestGroundItem(id: Int){
        val item = getNearestGroundItem(id)
        if(item != null)
            item.interaction?.handle(bot,item.interaction[2])
    }

    fun getNearestGameObject(loc: Location, objectId: Int): GameObject? {
        var nearestObject: GameObject? = null
        val minDistance = Double.MAX_VALUE
        for (o in RegionManager.forId(loc.regionId).planes[0].objects) {
            for (obj in o) {
                if (obj != null) {
                    if (distance(loc, obj) < minDistance && obj.id == objectId) {
                        nearestObject = obj
                    }
                }
            }
        }
        return nearestObject
    }

    private fun findTargets(entity: Entity?, radius: Int, name: String? = null): List<Entity>? {
        val targets: MutableList<Entity> = ArrayList()
        val localNPCs: Array<Any> = RegionManager.getLocalNpcs(entity, radius).toTypedArray()
        var length = localNPCs.size
        if (length > 5) {
            length = 5
        }
        for (i in 0 until length) {
            val npc = localNPCs[i] as NPC
            run { if (checkValidTargets(npc,name)) targets.add(npc) }
        }
        return if (targets.size == 0) null else targets
    }

    private fun checkValidTargets(target: NPC,name: String?): Boolean {
        if (!target.isActive) {
            return false
        }
        if (!target.properties.isMultiZone && target.inCombat()) {
            return false
        }
        if (name != null){
            if(target.name != name)
                return false
        }
        return target.definition.hasAction("attack")
    }

    fun attackNpcsInRadius(bot: Player, radius: Int): Boolean {
        if (bot.inCombat()) return true
        var creatures: List<Entity>? = findTargets(bot, radius) ?: return false
        bot.attack(creatures!![RandomFunction.getRandom(creatures.size - 1)])
        return if (creatures.isNotEmpty()) {
            true
        } else {
            creatures = findTargets(bot, radius)
            if (!creatures!!.isEmpty()) {
                bot.attack(creatures[RandomFunction.getRandom(creatures.size - 1)])
                return true
            }
            false
        }
    }

    fun walkTo(loc: Location){
        if(!bot.walkingQueue.isMoving) {
            Executors.newSingleThreadExecutor().execute {
                walkToIterator(loc)
            }
        }
    }

    private fun walkToIterator(loc: Location){
        var diffX = loc.x - bot.location.x
        var diffY = loc.y - bot.location.y
        while(!bot.location.transform(diffX,diffY,0).withinDistance(bot.location)) {
            diffX /= 2
            diffY /= 2
        }
        GameWorld.Pulser.submit(object : MovementPulse(bot,bot.location.transform(diffX,diffY,0), Pathfinder.SMART){
            override fun pulse(): Boolean {
                return true
            }
        })
    }

    fun attackNpcInRadius(bot: Player,name: String, radius: Int): Boolean {
        if (bot.inCombat()) return true
        var creatures: List<Entity>? = findTargets(bot, radius,name) ?: return false
        bot.attack(creatures!![RandomFunction.getRandom(creatures.size - 1)])
        return if (creatures.isNotEmpty()) {
            true
        } else {
            creatures = findTargets(bot, radius,name)
            if (!creatures!!.isEmpty()) {
                bot.attack(creatures.random())
                return true
            }
            false
        }
    }

    fun GroundItem.distance(loc: Location): Double{
        return location.getDistance(loc)
    }

    fun teleportToGE(){
        bot.lock()
        bot.visualize(ANIMATIONUP, GRAPHICSUP)
        bot.impactHandler.disabledTicks = 4
        val location = Location.create(3165, 3482, 0)
        bot.pulseManager.run(object : Pulse(4, bot) {
            override fun pulse(): Boolean {
                bot.unlock()
                bot.properties.teleportLocation = location
                bot.animator.reset()
                return true
            }
        })
    }

    fun sellOnGE(id: Int){
        class toCounterPulse : MovementPulse(bot,Location.create(3165, 3487, 0) ){
            override fun pulse(): Boolean {
                val offer = GrandExchangeOffer(id,true)
                val itemAmt = bot.bank.getAmount(id)
                offer.amount = itemAmt
                offer.offeredValue = checkPriceOverrides(id) ?: ItemDefinition.forId(id).value
                SystemLogger.log("Offered " + offer.amount)
                GEOfferDispatch.dispatch(Player(PlayerDetails.getDetails("2009scape")),offer)
                bot.bank.remove(Item(id,itemAmt))
                bot.bank.refresh()
                SystemLogger.log("Banked fish: " + bot.bank.getAmount(ItemNames.RAW_LOBSTER))
                return true
            }
        }
        bot.pulseManager.run(toCounterPulse())
    }

    fun sellAllOnGe(){
        class toCounterPulseAll : MovementPulse(bot,Location.create(3165, 3487, 0) ){
            override fun pulse(): Boolean {
                for(item in bot.bank.toArray()) {
                    item ?: continue
                    SystemLogger.log("Checking ${item.id}")
                    if(!item.definition.isTradeable) {continue}
                    SystemLogger.log("Adding ${item.name}")
                    val offer = GrandExchangeOffer(item.id, true)
                    val itemAmt = item.amount
                    offer.amount = itemAmt
                    offer.offeredValue = checkPriceOverrides(item.id) ?: item.definition.value
                    GEOfferDispatch.dispatch(bot, offer)
                    bot.bank.remove(item)
                    bot.bank.refresh()
                }
                return true
            }
        }
        bot.pulseManager.run(toCounterPulseAll())
    }

    fun sellOnGE(id: Int, value: Int){
        class toCounterPulseWithPrice : MovementPulse(bot,Location.create(3165, 3487, 0) ){
            override fun pulse(): Boolean {
                val offer = GrandExchangeOffer(id,true)
                val itemAmt = bot.bank.getAmount(id)
                offer.amount = itemAmt
                offer.offeredValue = checkPriceOverrides(id) ?: value
                SystemLogger.log("Offered " + offer.amount)
                GEOfferDispatch.dispatch(bot,offer)
                bot.bank.remove(Item(id,itemAmt))
                bot.bank.refresh()
                SystemLogger.log("Banked fish: " + bot.bank.getAmount(ItemNames.RAW_LOBSTER))
                return true
            }
        }
        bot.pulseManager.run(toCounterPulseWithPrice())
    }

    fun teleport(loc: Location){
        bot.lock()
        bot.visualize(ANIMATIONUP, GRAPHICSUP)
        bot.impactHandler.disabledTicks = 4
        val location = loc
        GameWorld.Pulser.submit(object : Pulse(4, bot) {
            override fun pulse(): Boolean {
                bot.unlock()
                bot.properties.teleportLocation = location
                bot.animator.reset()
                return true
            }
        })
    }

    fun bankItem(item: Int){
        class BankingPulse() : Pulse(20){
            override fun pulse(): Boolean {
                val logs = bot.inventory.getAmount(item)
                bot.inventory.remove(Item(item,logs))
                bot.bank.add(Item(item,logs))
                SystemLogger.log("${bot.username}: Banked $logs ${ItemDefinition.forId(item).name.toLowerCase()}")
                SystemLogger.log("${bot.username}: Bank currently contains ${bot.bank.getAmount(item)} ${ItemDefinition.forId(item).name.toLowerCase()}")
                return true
            }
        }
        bot.pulseManager.run(BankingPulse())
    }


    fun eat(foodId: Int) {
        val foodItem = Item(foodId)
        if (bot.skills.getStaticLevel(Skills.HITPOINTS) * RandomFunction.random(0.5,0.75) >= bot.skills.lifepoints && bot.inventory.containsItem(foodItem)) {
            bot.lock(3)
            //this.animate(new Animation(829));
            val food = bot.inventory.getItem(foodItem)
            var consumable: Consumable? = Consumables.getFoodByItemID(food.id)
            if (consumable == null) {
                consumable = Food(food.id, ConsumableProperties(1))
            }
            consumable.consume(food, bot)
            bot.properties.combatPulse.delayNextAttack(3)
        }
    }

    fun forceEat(foodId: Int) {
        val foodItem = Item(foodId)
        if (bot.inventory.containsItem(foodItem)) {
            bot.lock(3)
            //this.animate(new Animation(829));
            val food = bot.inventory.getItem(foodItem)
            var consumable: Consumable? = Consumables.getFoodByItemID(food.id)
            if (consumable == null) {
                consumable = Food(food.id, ConsumableProperties(1))
            }
            consumable.consume(food, bot)
            bot.properties.combatPulse.delayNextAttack(3)
        }
    }

    fun checkPriceOverrides(id: Int): Int?{
        return when(id){
            ItemNames.DRAGON_BONES ->          1250
            ItemNames.GREEN_DRAGONHIDE_1753 -> 550
            ItemNames.BOW_STRING_1777 ->       250
            ItemNames.MAGIC_LOGS_1513 ->       750
            ItemNames.GRIMY_RANARR ->          1214
            ItemNames.GRIMY_AVANTOE ->         453
            ItemNames.GRIMY_CADANTINE ->       232
            ItemNames.GRIMY_DWARF_WEED ->      86
            ItemNames.GRIMY_GUAM ->            50
            ItemNames.GRIMY_HARRALANDER ->     115
            ItemNames.GRIMY_IRIT ->            860
            ItemNames.GRIMY_KWUARM ->          334
            ItemNames.GRIMY_LANTADYME ->       115
            ItemNames.GRIMY_MARRENTILL ->      250
            else -> null
        }
    }
}