local colors = require("colors")
local opacity = addon:setting("opacity")

module:on("render2d.hud", 0, function(ui, event)
    local alpha = math.floor(opacity:get() * 255)
    ui:round_rect(8, 8, 92, 24, 4, colors.with_alpha(0x101418, alpha))
    ui:text("Epsilon Lua", 16, 14, 0.65, 0xFFFFFFFF)
end)
