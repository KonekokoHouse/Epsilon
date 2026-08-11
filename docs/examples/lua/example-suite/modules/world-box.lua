module:on("render3d", 0, function(render3d, event)
    if mc.player == nil then return end
    local pos = mc.player:position()
    local box = render3d:box(pos.x - 0.5, pos.y, pos.z - 0.5, pos.x + 0.5, pos.y + 1.0, pos.z + 0.5)
    render3d:filled_box(box, 0x4433AAFF)
    render3d:outline_box(box, 0xFF33AAFF, 2.0)
end)
