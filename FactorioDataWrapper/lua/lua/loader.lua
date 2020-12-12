defines = require("lib/defines")
serpent = require("lib/serpent")
require("dataloader")

function log(s)
  print(s)
end

function localised_print(s)
  print(serpent.line(s))
end

function table_size(t)
  local count = 0
  for _ in pairs(t) do
    count = count + 1
  end
  return count
end
