local general = addon:group("general")

addon:doubleSetting({
    id = "opacity",
    default = 0.8,
    min = 0.1,
    max = 1.0,
    step = 0.05,
    group = general
})
