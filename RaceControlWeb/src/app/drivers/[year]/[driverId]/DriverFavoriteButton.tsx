"use client";

import { useFavorites } from "@/components/FavoritesProvider";
import { StarButton } from "@/components/StateViews";

export function DriverFavoriteButton({ driverId, name }: { driverId: string; name: string }) {
  const { isFavoriteDriver, toggleFavoriteDriver } = useFavorites();
  return (
    <StarButton active={isFavoriteDriver(driverId)} onClick={() => toggleFavoriteDriver(driverId)} label={`Favorite ${name}`} />
  );
}
