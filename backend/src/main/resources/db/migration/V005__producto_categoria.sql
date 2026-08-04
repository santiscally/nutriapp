-- Categoría del producto (Rubro de Contabilium). La marca ya existe (columna `marca`),
-- que en productos de Contabilium se puebla desde el Subrubro. Ver ProductoSyncService.
ALTER TABLE productos ADD COLUMN IF NOT EXISTS categoria VARCHAR(120);
