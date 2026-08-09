local colors = {}

function colors.with_alpha(rgb, alpha)
    return alpha * 0x1000000 + rgb
end

return colors
