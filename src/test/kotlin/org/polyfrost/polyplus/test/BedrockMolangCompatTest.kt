package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.bedrock.animation.BedrockAnimationParser
import org.polyfrost.polyplus.client.bedrock.molang.BedrockMolang
import org.polyfrost.polyplus.client.bedrock.molang.MolangContext

class BedrockMolangCompatTest {
    @Test
    fun `unknown queries, functions and variables read as zero`() {
        val expression = BedrockMolang().expression(
            "q.head_y_rotation * 0.5 + math.random(0, 10) + q.some_future_query + v.never_declared + math.sin(q.anim_time * 45)",
        )
        assertEquals(1.0, expression.evaluate(MolangContext.forAnimation(2f)), 1e-6)
    }

    @Test
    fun `scripts with unknown names still store their variables`() {
        val molang = BedrockMolang()
        val script = requireNotNull(molang.script("v.swing = q.modified_move_speed + 3; c.unused = math.random(1, 2);"))
        val variables = mutableMapOf<String, Float>()
        script.execute(MolangContext.forAnimation(0f, variables = variables))
        assertEquals(3f, variables["v.swing"])
        assertEquals(3.0, molang.expression("v.swing").evaluate(MolangContext.forAnimation(0f, variables = variables)))
    }

    @Test
    fun `animations using Bedrock-only names still parse`() {
        val json = """
            {
              "format_version": "1.8.0",
              "animations": {
                "animation.test.look": {
                  "loop": true,
                  "animation_length": 1.0,
                  "initialize": "v.offset = math.random(0, 1);",
                  "bones": {
                    "head": {
                      "rotation": ["q.head_x_rotation", "q.head_y_rotation + v.offset", "math.die_roll(1, 1, 6) * 0"],
                      "position": [0, "math.sin(q.anim_time * 360) + q.is_using_item", 0]
                    }
                  }
                }
              }
            }
        """.trimIndent()
        val file = BedrockAnimationParser.parseStream(json.byteInputStream())
        assertTrue("animation.test.look" in file.animations.keys, file.animations.keys.toString())
    }
}
