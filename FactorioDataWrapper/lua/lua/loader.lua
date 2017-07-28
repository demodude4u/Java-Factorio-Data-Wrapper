require "fixmodule"

local inspect = require "inspect"

defines = {
    difficulty_settings = {
        recipe_difficulty = {
            normal = "normal",
            expensive = "expensive"
        },
        technology_difficulty = {
            normal = "normal",
            expensive = "expensive"
        }
    },
    direction = {
        north = 0,
        east = 2,
        south = 4,
        west = 6
    }
}

require "dataloader"
-- require "core.data"
-- require "base.data"
-- require "base.data-updates"

data.raw["gui-style"] = {
    default = {}
}