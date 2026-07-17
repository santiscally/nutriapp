-- Seed inicial del catálogo (regla de oro: nada mockeado en memoria — la data vive en la DB).
-- Productos realistas estilo tienda TBC (suplementos). Idempotente: no duplica por SKU.
-- En Fase 2 el sync TiendaNube concilia por SKU y actualiza origen/ids externos.

INSERT INTO productos (sku, nombre, descripcion, precio, stock, marca, laboratorio, principio_activo, presentacion, origen)
SELECT v.sku, v.nombre, v.descripcion, v.precio, v.stock, v.marca, v.laboratorio, v.principio_activo, v.presentacion, 'SEED'
FROM (VALUES
    ('WHEY-CHOC-1KG',  'Whey Protein Chocolate 1kg',        'Proteína de suero concentrada sabor chocolate.',            45900.00, 25, 'Star Nutrition',   'Star Nutrition Lab',  'proteína de suero (whey concentrada)', 'polvo 1kg'),
    ('WHEY-VAIN-1KG',  'Whey Protein Vainilla 1kg',         'Proteína de suero concentrada sabor vainilla.',             45900.00, 18, 'Star Nutrition',   'Star Nutrition Lab',  'proteína de suero (whey concentrada)', 'polvo 1kg'),
    ('CREA-MONO-300',  'Creatina Monohidrato 300g',         'Creatina monohidrato micronizada pura.',                    32500.00, 40, 'ENA Sport',        'ENA Lab',             'creatina monohidrato',                 'polvo 300g'),
    ('COLAG-HIDRO-30', 'Colágeno Hidrolizado x30 sobres',   'Colágeno hidrolizado con vitamina C y magnesio.',           28900.00, 30, 'Gentech',          'Gentech Lab',         'colágeno hidrolizado',                 'sobres x30'),
    ('OMEGA3-60',      'Omega 3 Fish Oil x60 cápsulas',     'Aceite de pescado EPA/DHA alta pureza.',                    19900.00, 50, 'Universal',        'Universal Lab',       'ácidos grasos omega 3 (EPA/DHA)',      'cápsulas x60'),
    ('MULTI-VIT-90',   'Multivitamínico x90 comprimidos',   'Complejo multivitamínico y minerales.',                     15900.00, 60, 'Nutrilab',         'Nutrilab',            'multivitamínico',                      'comprimidos x90'),
    ('MAGNE-CIT-200',  'Magnesio Citrato 200g',             'Citrato de magnesio en polvo, alta absorción.',             17500.00, 35, 'Nutremax',         'Nutremax Lab',        'citrato de magnesio',                  'polvo 200g'),
    ('VIT-D3-2000',    'Vitamina D3 2000UI x60',            'Colecalciferol 2000UI por cápsula.',                        12900.00, 45, 'Nutrilab',         'Nutrilab',            'colecalciferol (vitamina D3)',         'cápsulas x60'),
    ('PROT-VEG-750',   'Proteína Vegetal Vainilla 750g',    'Blend de proteína de arveja y arroz, sin lactosa.',         39900.00, 15, 'Pulver',           'Pulver Lab',          'proteína vegetal (arveja/arroz)',      'polvo 750g'),
    ('BCAA-2-1-1',     'BCAA 2:1:1 x120 cápsulas',          'Aminoácidos ramificados leucina/isoleucina/valina.',        21900.00, 28, 'ENA Sport',        'ENA Lab',             'aminoácidos ramificados (BCAA)',       'cápsulas x120'),
    ('FIBRA-PSY-250',  'Fibra Psyllium 250g',               'Cáscara de psyllium para tránsito intestinal.',             13500.00, 33, 'Natier',           'Natier Lab',          'psyllium husk',                        'polvo 250g'),
    ('PROBIO-30',      'Probióticos x30 cápsulas',          'Mezcla de cepas probióticas 10 billones UFC.',              24900.00, 22, 'Natier',           'Natier Lab',          'lactobacillus/bifidobacterium',        'cápsulas x30')
) AS v(sku, nombre, descripcion, precio, stock, marca, laboratorio, principio_activo, presentacion)
WHERE NOT EXISTS (SELECT 1 FROM productos p WHERE p.sku = v.sku);
