package com.lkroll.ep.compendium

import utils.OptionPickler.{ ReadWriter => RW, macroRW }

case class Ammo(name: String, descr: String, appliesTo: List[WeaponType],
                apMod: APMod = APMod.Id, dmgMod: DamageMod = DamageMod.Id, typeMod: DamageTypeMod = DamageTypeMod.Id,
                price: Cost, source: String) extends ChatRenderable {
  def appliesTo(t: WeaponType): Boolean = appliesTo.contains(t);

  override def templateTitle: String = name;
  override def templateSubTitle: String = "Ammo for " + appliesTo.mkString(", ");
  override def templateKV: Map[String, String] = apMod.templateKV ++
    dmgMod.templateKV ++
    price.templateKV ++
    Map("Source" -> source);
  override def templateDescr: String = descr;
}
object Ammo {
  implicit def rw: RW[Ammo] = macroRW;
  val dataType: String = "ammo";
}

case class WeaponWithAmmo(weapon: Weapon, ammo: Ammo) extends ChatRenderable {
  def name: String = s"${weapon.name}≣${ammo.name}";
  def descr: String = weapon.descr + "\n---\n" + ammo.descr;
  def dmgD10: Int = ammo.dmgMod.modifyD10(weapon.dmgD10);
  def dmgDiv: Int = ammo.dmgMod.modifyDiv(weapon.dmgD10, weapon.dmgDiv);
  def dmgConst: Int = ammo.dmgMod.modifyConst(weapon.dmgConst);
  def dmgType: DamageType = ammo.typeMod.modify(weapon.dmgType);
  def effect: Option[String] = ammo.dmgMod.modifyEffect(weapon.effect);
  def ap: Int = ammo.apMod.modifyAP(weapon.ap);

  override def templateTitle: String = name;
  override def templateSubTitle: String = this.weapon.`type` match {
    case _: WeaponType.Melee  => "Melee Weapon with Ammo" // doesn't make much sense^^
    case _: WeaponType.Ranged => "Ranged Weapon with Ammo"
    case _: WeaponType.Thrown => "Thrown Weapon with Ammo"
  };
  override def templateKV: Map[String, String] = this.weapon.`type`.templateKV ++
    Map("Damage" -> dmgString) ++
    weapon.dmgType.templateKV ++
    (effect.map(s => Map("Effect" -> s)).getOrElse(Map.empty)) ++
    Map("AP" -> ap.toString) ++
    weapon.range.templateKV ++
    (weapon.gun.map(g => g.templateKV).getOrElse(Map.empty)) ++
    Map("Source" -> weapon.source);
  override def templateDescr: String = descr;

  def summaryString: String = effect match {
    case Some(e) => s"$name (${dmgString} DV, AP $ap, $e)"
    case None    => s"$name (${dmgString} DV, AP $ap)"
  };

  def dmgString: String = if (weapon.dmgDiv == 1) s"${dmgD10}d10+${dmgConst}" else s"${dmgD10}d10%${dmgDiv}+${dmgConst}";
}

sealed trait APMod extends ChatRenderable {
  def modifyAP(ap: Int): Int;
  def text: String;
  override def templateKV: Map[String, String] = Map("AP Modifier" -> text);
}
object APMod {
  implicit def rw: RW[APMod] = RW.merge(
    macroRW[Id.type],
    macroRW[Const]);

  @upickle.key("Id")
  case object Id extends APMod {
    override def modifyAP(ap: Int): Int = ap;
    override def text: String = "–";
  }
  @upickle.key("Const")
  case class Const(apMod: Int) extends APMod {
    override def modifyAP(ap: Int): Int = Math.min(0, ap + apMod);
    override def text: String = apMod.toString();
  }
}

sealed trait DamageTypeMod extends ChatRenderable {
  def modify(damageType: DamageType): DamageType;
  def text: String;
  override def templateKV: Map[String, String] = Map("Damage Type Modifier" -> text);
}
object DamageTypeMod {
  implicit def rw: RW[DamageTypeMod] = RW.merge(
    macroRW[Id.type],
    macroRW[Replace]);

  @upickle.key("Id")
  case object Id extends DamageTypeMod {
    override def modify(damageType: DamageType): DamageType = damageType;
    override def text: String = "–";
  }
  @upickle.key("Replace")
  case class Replace(damageType: DamageType) extends DamageTypeMod {
    override def modify(damageType: DamageType): DamageType = this.damageType;
    override def text: String = this.damageType.toString();
  }
}

sealed trait DamageMod extends ChatRenderable {
  def modifyD10(dmg: Int): Int;
  def modifyDiv(dmgD10: Int, divisor: Int): Int;
  def modifyConst(dmg: Int): Int;
  def modifyEffect(effect: Option[String]): Option[String];
  def ++(mod: DamageMod): DamageMod.Chain = mod match {
    case DamageMod.Chain(mods) => DamageMod.Chain(this :: mods)
    case _                     => DamageMod.Chain(List(this, mod))
  }
  def text: String;
  override def templateKV: Map[String, String] = Map("DV Modifier" -> text);
}
object DamageMod {
  implicit def rw: RW[DamageMod] = RW.merge(
    macroRW[NoDamage.type],
    macroRW[Id.type],
    macroRW[Half.type],
    macroRW[Const],
    macroRW[Effect],
    macroRW[Chain]);

  @upickle.key("NoDamage")
  case object NoDamage extends DamageMod {
    override def modifyD10(dmg: Int): Int = 0;
    override def modifyDiv(dmgD10: Int, divisor: Int): Int = 1;
    override def modifyConst(dmg: Int): Int = 0;
    override def modifyEffect(effect: Option[String]): Option[String] = effect;
    override def text: String = "no damage";
  }
  @upickle.key("Id")
  case object Id extends DamageMod {
    override def modifyD10(dmg: Int): Int = dmg;
    override def modifyDiv(dmgD10: Int, divisor: Int): Int = divisor;
    override def modifyConst(dmg: Int): Int = dmg;
    override def modifyEffect(effect: Option[String]): Option[String] = effect;
    override def text: String = "–";
  }
  @upickle.key("Half")
  case object Half extends DamageMod {
    override def modifyD10(dmg: Int): Int = if (dmg % 2 == 0) dmg / 2 else dmg;
    override def modifyDiv(dmgD10: Int, divisor: Int): Int = if (dmgD10 % 2 == 0) 1 else 2;
    override def modifyConst(dmg: Int): Int = ceilDiv(dmg, 2);
    override def modifyEffect(effect: Option[String]): Option[String] = effect;
    override def text: String = "-half";
  }
  @upickle.key("Const")
  case class Const(d10Dmg: Int, constDmg: Int) extends DamageMod {
    override def modifyD10(dmg: Int): Int = Math.max(0, dmg + d10Dmg);
    override def modifyDiv(dmgD10: Int, divisor: Int): Int = divisor; // TODO this isn't actually quite right if divisor != 1
    override def modifyConst(dmg: Int): Int = Math.max(0, dmg + constDmg);
    override def modifyEffect(effect: Option[String]): Option[String] = effect;
    override def text: String = {
      val d10S = if (d10Dmg > 0) s"+$d10Dmg" else d10Dmg.toString();
      val constS = if (constDmg > 0) s"+$constDmg" else if (constDmg == 0) "" else constDmg.toString();
      if (d10Dmg == 0) constS else s"${d10S}d10$constS"
    }
  }
  @upickle.key("Effect")
  case class Effect(addEffect: String) extends DamageMod {
    override def modifyD10(dmg: Int): Int = dmg;
    override def modifyDiv(dmgD10: Int, divisor: Int): Int = divisor;
    override def modifyConst(dmg: Int): Int = dmg;
    override def modifyEffect(effect: Option[String]): Option[String] = effect match {
      case Some(s) => Some(s + " + " + addEffect)
      case None    => Some(addEffect)
    }
    override def text: String = addEffect;
  }

  @upickle.key("Chain")
  case class Chain(mods: List[DamageMod]) extends DamageMod {
    override def modifyD10(dmg: Int): Int = mods.foldLeft(dmg)((acc, mod) => mod.modifyD10(acc));
    override def modifyDiv(dmgD10: Int, divisor: Int): Int = mods.foldLeft((dmgD10, divisor))((acc, mod) => acc match {
      case (d10, div) => (d10, mod.modifyDiv(d10, div))
    })._2;
    override def modifyConst(dmg: Int): Int = mods.foldLeft(dmg)((acc, mod) => mod.modifyConst(acc));
    override def modifyEffect(effect: Option[String]): Option[String] =
      mods.foldLeft(effect)((acc, mod) => mod.modifyEffect(acc));
    override def ++(mod: DamageMod): Chain = mod match {
      case Chain(mods) => Chain(this.mods ++ mods)
      case _           => Chain(this.mods ++ List(mod))
    }
    override def text: String = mods.foldLeft("")((acc, mod) => acc + " + " + mod.text);
  }
}
