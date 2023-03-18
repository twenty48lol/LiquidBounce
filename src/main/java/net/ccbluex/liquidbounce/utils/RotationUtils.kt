/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.utils

import net.ccbluex.liquidbounce.LiquidBounce
import net.ccbluex.liquidbounce.event.EventTarget
import net.ccbluex.liquidbounce.event.Listenable
import net.ccbluex.liquidbounce.event.PacketEvent
import net.ccbluex.liquidbounce.event.TickEvent
import net.ccbluex.liquidbounce.features.module.modules.combat.FastBow
import net.ccbluex.liquidbounce.utils.RaycastUtils.raycastEntity
import net.ccbluex.liquidbounce.utils.extensions.hitBox
import net.ccbluex.liquidbounce.utils.misc.RandomUtils.nextDouble
import net.minecraft.entity.Entity
import net.minecraft.network.play.client.C03PacketPlayer
import net.minecraft.util.*
import java.util.*
import kotlin.math.*

object RotationUtils : MinecraftInstance(), Listenable {
    /**
     * Handle minecraft tick
     *
     * @param event Tick event
     */
    @EventTarget
    fun onTick(event: TickEvent) {
        if (targetRotation != null) {
            keepLength--
            if (keepLength <= 0) reset()
        }
        if (random.nextGaussian() > 0.8) x = Math.random()
        if (random.nextGaussian() > 0.8) y = Math.random()
        if (random.nextGaussian() > 0.8) z = Math.random()
    }

    /**
     * Handle packet
     *
     * @param event Packet Event
     */
    @EventTarget
    fun onPacket(event: PacketEvent) {
        val packet = event.packet
        if (packet is C03PacketPlayer) {
            targetRotation?.let { targetRotation ->
                if (!keepCurrentRotation && (targetRotation.yaw != serverRotation.yaw || targetRotation.pitch != serverRotation.pitch)) {
                    packet.yaw = targetRotation.yaw
                    packet.pitch = targetRotation.pitch
                    packet.rotating = true
                }
            }

            if (packet.rotating) serverRotation = Rotation(packet.getYaw(), packet.getPitch())
        }
    }

    /**
     * @return YESSSS!
     */
    override fun handleEvents() = true

    private var keepLength = 0
    @JvmField
    var targetRotation: Rotation? = null
    @JvmField
    var serverRotation: Rotation = Rotation(0f, 0f)
    @JvmField
    var keepCurrentRotation = false

    private val random = Random()
    private var x = random.nextDouble()
    private var y = random.nextDouble()
    private var z = random.nextDouble()

    /**
     * Face block
     *
     * @param blockPos target block
     */
    fun faceBlock(blockPos: BlockPos?): VecRotation? {
        if (blockPos == null) return null
        var vecRotation: VecRotation? = null
        val eyesPos = mc.thePlayer.getPositionEyes(1f)
        val startPos = Vec3(blockPos)
        var xSearch = 0.1
        while (xSearch < 0.9) {
            var ySearch = 0.1
            while (ySearch < 0.9) {
                var zSearch = 0.1
                while (zSearch < 0.9) {
                    val posVec = startPos.addVector(xSearch, ySearch, zSearch)
                    val dist = eyesPos.distanceTo(posVec)

                    val diffX = posVec.xCoord - eyesPos.xCoord
                    val diffY = posVec.yCoord - eyesPos.yCoord
                    val diffZ = posVec.zCoord - eyesPos.zCoord
                    val diffXZ = sqrt(diffX * diffX + diffZ * diffZ)

                    val rotation = Rotation(
                        MathHelper.wrapAngleTo180_float(Math.toDegrees(atan2(diffZ, diffX)).toFloat() - 90f),
                        MathHelper.wrapAngleTo180_float(-Math.toDegrees(atan2(diffY, diffXZ)).toFloat())
                    )

                    val rotationVector = getVectorForRotation(rotation)
                    val vector = eyesPos.addVector(
                        rotationVector.xCoord * dist,
                        rotationVector.yCoord * dist,
                        rotationVector.zCoord * dist
                    )

                    mc.theWorld.rayTraceBlocks(eyesPos, vector, false, false, true)?.let {
                        if (it.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
                            val currentVec = VecRotation(posVec, rotation)
                            if (vecRotation == null || getRotationDifference(currentVec.rotation) < getRotationDifference(vecRotation!!.rotation))
                                vecRotation = currentVec
                        }
                    }
                    zSearch += 0.1
                }
                ySearch += 0.1
            }
            xSearch += 0.1
        }
        return vecRotation
    }

    /**
     * Face target with bow
     *
     * @param target      your enemy
     * @param silent      client side rotations
     * @param predict     predict new enemy position
     * @param predictSize predict size of predict
     */
    fun faceBow(target: Entity, silent: Boolean, predict: Boolean, predictSize: Float) {
        val player = mc.thePlayer

        val posX = target.posX + (if (predict) (target.posX - target.prevPosX) * predictSize else .0) - (player.posX + if (predict) player.posX - player.prevPosX else .0)
        val posY = target.entityBoundingBox.minY + (if (predict) (target.entityBoundingBox.minY - target.prevPosY) * predictSize else .0) + target.eyeHeight - 0.15 - (player.entityBoundingBox.minY + (if (predict) player.posY - player.prevPosY else .0)) - player.getEyeHeight()
        val posZ = target.posZ + (if (predict) (target.posZ - target.prevPosZ) * predictSize else .0) - (player.posZ + if (predict) player.posZ - player.prevPosZ else .0)
        val posSqrt = sqrt(posX * posX + posZ * posZ)

        var velocity = if (LiquidBounce.moduleManager.getModule(FastBow::class.java).state) 1f else player.itemInUseDuration / 20f
        velocity = min((velocity * velocity + velocity * 2) / 3, 1f)

        val rotation = Rotation(
            Math.toDegrees(atan2(posZ, posX)).toFloat() - 90,
            -Math.toDegrees(atan((velocity * velocity - sqrt(velocity * velocity * velocity * velocity - 0.006f * (0.006f * posSqrt * posSqrt + 2 * posY * velocity * velocity))) / (0.006f * posSqrt))).toFloat()
        )
        if (silent)
            setTargetRotation(rotation)
        else limitAngleChange(
            Rotation(player.rotationYaw, player.rotationPitch),
            rotation,
            10f + Random().nextInt(6)
        ).toPlayer(mc.thePlayer)
    }

    /**
     * Translate vec to rotation
     *
     * @param vec     target vec
     * @param predict predict new location of your body
     * @return rotation
     */
    fun toRotation(vec: Vec3, predict: Boolean): Rotation {
        val eyesPos = mc.thePlayer.getPositionEyes(1f)
        if (predict) eyesPos.addVector(mc.thePlayer.motionX, mc.thePlayer.motionY, mc.thePlayer.motionZ)
        val diffX = vec.xCoord - eyesPos.xCoord
        val diffY = vec.yCoord - eyesPos.yCoord
        val diffZ = vec.zCoord - eyesPos.zCoord
        return Rotation(
            MathHelper.wrapAngleTo180_float(
                Math.toDegrees(atan2(diffZ, diffX)).toFloat() - 90f
            ),
            MathHelper.wrapAngleTo180_float(
                -Math.toDegrees(atan2(diffY, sqrt(diffX * diffX + diffZ * diffZ))).toFloat()
            )
        )
    }

    /**
     * Get the center of a box
     *
     * @param bb your box
     * @return center of box
     */
    fun getCenter(bb: AxisAlignedBB): Vec3 {
        return Vec3(
            bb.minX + (bb.maxX - bb.minX) * 0.5,
            bb.minY + (bb.maxY - bb.minY) * 0.5,
            bb.minZ + (bb.maxZ - bb.minZ) * 0.5
        )
    }

    /**
     * Search good center
     *
     * @param bb           enemy box
     * @param outborder    outborder option
     * @param random       random option
     * @param predict      predict option
     * @param throughWalls throughWalls option
     * @return center
     */
    fun searchCenter(
        bb: AxisAlignedBB, outborder: Boolean, random: Boolean,
        predict: Boolean, throughWalls: Boolean, distance: Float
    ): VecRotation? {
        if (outborder) {
            val vec3 = Vec3(
                bb.minX + (bb.maxX - bb.minX) * (x * 0.3 + 1.0),
                bb.minY + (bb.maxY - bb.minY) * (y * 0.3 + 1.0),
                bb.minZ + (bb.maxZ - bb.minZ) * (z * 0.3 + 1.0)
            )
            return VecRotation(vec3, toRotation(vec3, predict))
        }

        val randomVec = Vec3(
            bb.minX + (bb.maxX - bb.minX) * x * 0.8999,
            bb.minY + (bb.maxY - bb.minY) * y * 0.8599,
            bb.minZ + (bb.maxZ - bb.minZ) * z * 0.8999
        )

        val randomRotation = toRotation(randomVec, predict)

        val eyes = mc.thePlayer.getPositionEyes(1f)
        var vecRotation: VecRotation? = null

        val horizontalStart = if (random) nextDouble(0.10, 0.15) else 0.15
        val horizontalEnd = if (random) nextDouble(0.10, 0.15) else 0.85
        val verticalStart = if (random) nextDouble(0.05, 0.15) else 0.15
        val verticalEnd = if (random) nextDouble(0.9, 1.0) else 1.0

        // Random might increase steps which will affect performance
        var x = horizontalStart
        while (x < horizontalEnd) {

            var y = verticalStart
            while (y < verticalEnd) {

                var z = horizontalStart
                while (z < horizontalEnd) {

                    val vec = Vec3(
                        bb.minX + (bb.maxX - bb.minX) * x,
                        bb.minY + (bb.maxY - bb.minY) * y,
                        bb.minZ + (bb.maxZ - bb.minZ) * z
                    )

                    val rotation = toRotation(vec, predict)
                    val vecDist = eyes.distanceTo(vec)
                    if (vecDist < distance) {
                        if (throughWalls || isVisible(vec)) {
                            val currentVec = VecRotation(vec, rotation)
                            if (vecRotation == null || (
                                        if (random) getRotationDifference(currentVec.rotation, randomRotation) < getRotationDifference(vecRotation.rotation, randomRotation)
                                        else getRotationDifference(currentVec.rotation) < getRotationDifference(vecRotation.rotation)
                                        )) vecRotation = currentVec
                        }
                    }
                    z += if (random) nextDouble(0.05, 0.1) else 0.1
                }
                y += if (random) nextDouble(0.05, 0.1) else 0.1
            }
            x += if (random) nextDouble(0.05, 0.1) else 0.1
        }
        return vecRotation
    }

    /**
     * Calculate difference between the client rotation and your entity
     *
     * @param entity your entity
     * @return difference between rotation
     */
    fun getRotationDifference(entity: Entity): Float {
        val rotation = toRotation(getCenter(entity.hitBox), true)
        return getRotationDifference(rotation, Rotation(mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch))
    }

    /**
     * Calculate difference between the server rotation and your rotation
     *
     * @param rotation your rotation
     * @return difference between rotation
     */
    @JvmStatic
    fun getRotationDifference(rotation: Rotation): Float {
        return getRotationDifference(rotation, serverRotation)
    }

    /**
     * Calculate difference between two rotations
     *
     * @param a rotation
     * @param b rotation
     * @return difference between rotation
     */
    fun getRotationDifference(a: Rotation, b: Rotation): Float {
        return hypot(getAngleDifference(a.yaw, b.yaw), (a.pitch - b.pitch))
    }

    /**
     * Limit your rotation using a turn speed
     *
     * @param currentRotation your current rotation
     * @param targetRotation your goal rotation
     * @param turnSpeed your turn speed
     * @return limited rotation
     */
    fun limitAngleChange(currentRotation: Rotation, targetRotation: Rotation, turnSpeed: Float): Rotation {
        val yawDifference = getAngleDifference(targetRotation.yaw, currentRotation.yaw)
        val pitchDifference = getAngleDifference(targetRotation.pitch, currentRotation.pitch)

        return Rotation(
            currentRotation.yaw + if (yawDifference > turnSpeed) turnSpeed else max(yawDifference, -turnSpeed),
            currentRotation.pitch + if (pitchDifference > turnSpeed) turnSpeed else max(pitchDifference, -turnSpeed)
        )
    }

    /**
     * Calculate difference between two angle points
     *
     * @param a angle point
     * @param b angle point
     * @return difference between angle points
     */
    fun getAngleDifference(a: Float, b: Float): Float {
        return ((a - b) % 360f + 540f) % 360f - 180f
    }

    /**
     * Calculate rotation to vector
     *
     * @param rotation your rotation
     * @return target vector
     */
    @JvmStatic
    fun getVectorForRotation(rotation: Rotation): Vec3 {
        val yawCos = cos(-rotation.yaw * 0.017453292 - Math.PI)
        val yawSin = sin(-rotation.yaw * 0.017453292 - Math.PI)
        val pitchCos = -cos(-rotation.pitch * 0.017453292)
        val pitchSin = sin(-rotation.pitch * 0.017453292)
        return Vec3(yawSin * pitchCos, pitchSin, yawCos * pitchCos)
    }

    /**
     * Allows you to check if your crosshair is over your target entity
     *
     * @param targetEntity       your target entity
     * @param blockReachDistance your reach
     * @return if crosshair is over target
     */
    fun isFaced(targetEntity: Entity, blockReachDistance: Double): Boolean {
        return raycastEntity(blockReachDistance) { entity: Entity -> targetEntity == entity } != null
    }

    /**
     * Allows you to check if your crosshair is over your target entity
     *
     * @param targetEntity       your target entity
     * @param blockReachDistance your reach
     * @return if crosshair is over target
     */
    fun isRotationFaced(targetEntity: Entity, blockReachDistance: Double, rotation: Rotation): Boolean {
        return raycastEntity(
            blockReachDistance,
            rotation.yaw,
            rotation.pitch
        ) { entity: Entity -> targetEntity == entity } != null
    }

    /**
     * Allows you to check if your enemy is behind a wall
     */
    fun isVisible(vec3: Vec3): Boolean {
        val eyesPos = mc.thePlayer.getPositionEyes(1f)
        return mc.theWorld.rayTraceBlocks(eyesPos, vec3) == null
    }

    /**
     * Set your target rotation
     *
     * @param rotation your target rotation
     */
    fun setTargetRotation(rotation: Rotation, keepLength: Int = 0) {
        if (rotation.yaw.isNaN() || rotation.pitch.isNaN() || rotation.pitch > 90 || rotation.pitch < -90) return
        rotation.fixedSensitivity()
        targetRotation = rotation
        this.keepLength = keepLength
    }

    /**
     * Reset your target rotation
     */
    fun reset() {
        keepLength = 0
        targetRotation = null
    }

    /**
     * Returns the smallest angle change possible with a sensitivity
     */
    fun getFixedAngleDelta(sensitivity: Float = mc.gameSettings.mouseSensitivity): Float {
        val f = sensitivity * 0.6f + 0.2f
        return f * f * f * 1.2f
    }

    /**
     * Returns angle that is legitimately accomplishable with current sensitivity
     */
    @JvmStatic
    fun getFixedSensitivityAngle(targetAngle: Float, startAngle: Float = 0f): Float {
        val gcd = getFixedAngleDelta()
        val angleDelta = targetAngle - startAngle
        return startAngle + (angleDelta / gcd).roundToInt() * gcd
    }
}