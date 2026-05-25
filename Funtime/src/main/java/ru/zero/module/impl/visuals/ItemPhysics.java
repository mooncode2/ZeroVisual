package ru.zero.module.impl.visuals;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import ru.zero.module.api.Category;
import ru.zero.module.api.IModule;
import ru.zero.module.api.Module;

@IModule(
   name = "ItemPhysics",
   description = "",
   category = Category.Visuals,
   bind = -1
)
@Environment(EnvType.CLIENT)
public class ItemPhysics extends Module {
}
