-- First intro text for each category: what the shop sells there, what it checks, and what a buyer
-- should look at before choosing. Only fills an empty intro, so text written in the admin is kept.
-- Uses only the tags the admin sanitiser allows (p, strong, em, ul, ol, li, a).

UPDATE category SET intro_html = '<p>Karta grafike të reja dhe të përdorura në Tiranë, nga NVIDIA GeForce RTX dhe GTX te AMD Radeon RX. Çdo kartë e përdorur testohet nën ngarkesë (stress test dhe lojëra) para se ta listojmë, dhe temperaturat e gjendja shkruhen hapur te produkti. Paguani në dorëzim dhe dërgojmë me korrier në gjithë Shqipërinë.</p>
<p>Para se të zgjidhni, kontrolloni tri gjëra:</p>
<ul>
<li><strong>Furnizuesin (PSU)</strong>: sa W ka dhe a ka konektorët që kërkon karta.</li>
<li><strong>Gjatësinë</strong>: a hyn karta në kasën tuaj.</li>
<li><strong>Memorien (VRAM)</strong>: 8 GB janë minimumi i arsyeshëm për lojërat e reja në 1080p; për 1440p zgjidhni 12 GB ose më shumë.</li>
</ul>
<p>Doni të ndërroni kartën e vjetër? Shikoni <a href="/nderro">Nderro</a>. Nuk e gjeni modelin që kërkoni? <a href="/kerko-produkt">Na thoni çfarë ju duhet</a> dhe ju telefonojmë kur e gjejmë.</p>'
WHERE slug = 'karta-grafike' AND intro_html IS NULL;

UPDATE category SET intro_html = '<p>Procesorë AMD Ryzen dhe Intel Core, të rinj dhe të përdorur, në Tiranë. Çdo procesor i përdorur testohet para se ta listojmë, dhe te produkti shkruajmë soketin, bërthamat dhe frekuencat, që t''i krahasoni lehtë.</p>
<ul>
<li><strong>Soketi</strong> duhet të përputhet me <a href="/kategori/pllaka-ame">pllakën amë</a> (p.sh. AM4, AM5 ose LGA1700).</li>
<li><strong>Ftohësi</strong>: disa procesorë nuk e kanë në kuti; shikoni fushën "Me ftohës" dhe <a href="/kategori/ftohje">ftohësit</a> tanë.</li>
<li><strong>Grafika e integruar</strong>: pa të, ju duhet një <a href="/kategori/karta-grafike">kartë grafike</a>.</li>
</ul>
<p>Pagesë në dorëzim dhe dërgesë në gjithë Shqipërinë. Nëse doni gjithë kompjuterin, e ndërtojmë ne <a href="/pc-me-porosi">sipas buxhetit tuaj</a>.</p>'
WHERE slug = 'procesore' AND intro_html IS NULL;

UPDATE category SET intro_html = '<p>Pllaka amë të reja dhe të përdorura për AMD dhe Intel, në Tiranë. Te çdo produkt gjeni soketin, çipsetin, tipin e RAM-it dhe formatin, që të dini menjëherë nëse përshtatet me pjesët që keni.</p>
<ul>
<li><strong>Soketi dhe çipseti</strong>: procesori duhet të mbështetet nga pllaka; për disa procesorë më të rinj duhet përditësim i BIOS-it.</li>
<li><strong>RAM-i</strong>: DDR4 dhe DDR5 nuk zëvendësojnë njëra-tjetrën. Shikoni <a href="/kategori/memorie-ram">memoriet RAM</a>.</li>
<li><strong>Formati</strong>: ATX, Micro-ATX ose Mini-ITX duhet të hyjë në <a href="/kategori/kasa">kasën</a> tuaj.</li>
</ul>
<p>Pagesë në dorëzim, dërgesë në gjithë Shqipërinë ose marrje falas në Tiranë.</p>'
WHERE slug = 'pllaka-ame' AND intro_html IS NULL;

UPDATE category SET intro_html = '<p>Memorie RAM DDR4 dhe DDR5 për kompjuterë desktop, të reja dhe të përdorura, në Tiranë. Çdo kit i përdorur testohet para shitjes, dhe te produkti shkruajmë kapacitetin, shpejtësinë dhe latencën (CL).</p>
<ul>
<li><strong>Tipi</strong> duhet të jetë ai që mbështet <a href="/kategori/pllaka-ame">pllaka amë</a>: DDR4 ose DDR5.</li>
<li><strong>Dy module</strong> (p.sh. 2×8 GB) punojnë në dual channel dhe janë më të shpejta se një modul i vetëm.</li>
<li><strong>16 GB</strong> mjaftojnë për shumicën e lojërave; për montim videoje ose streaming zgjidhni 32 GB.</li>
</ul>
<p>Pagesë në dorëzim dhe dërgesë në gjithë Shqipërinë.</p>'
WHERE slug = 'memorie-ram' AND intro_html IS NULL;

UPDATE category SET intro_html = '<p>Disqe SSD NVMe, SSD SATA dhe HDD, të rinj dhe të përdorur, në Tiranë. Për një disk të përdorur, orët e punës tregojnë më shumë se mosha; i shënojmë te specifikimet kur i kemi.</p>
<ul>
<li><strong>NVMe</strong> është më i shpejti, por kërkon një slot M.2 në <a href="/kategori/pllaka-ame">pllakën amë</a>.</li>
<li><strong>SSD SATA</strong> përshtatet me pothuajse çdo kompjuter dhe është përmirësim i madh krahasuar me një HDD.</li>
<li><strong>HDD</strong> mbetet zgjidhja më e lirë për shumë hapësirë, si filma dhe arkiva.</li>
</ul>
<p>Pagesë në dorëzim dhe dërgesë në gjithë Shqipërinë.</p>'
WHERE slug = 'disqe' AND intro_html IS NULL;

UPDATE category SET intro_html = '<p>Furnizues energjie (PSU) të rinj dhe të përdorur, në Tiranë. Furnizuesi ushqen gjithë kompjuterin, prandaj te çdo produkt gjeni fuqinë, certifikimin 80+ dhe konektorët PCIe për kartën grafike.</p>
<ul>
<li><strong>Fuqia</strong>: shikoni sa W kërkon <a href="/kategori/karta-grafike">karta grafike</a> dhe lini rezervë.</li>
<li><strong>Certifikimi 80+</strong> (Bronze, Gold…) tregon efiçencën: më pak nxehtësi dhe më pak zhurmë.</li>
<li><strong>Modular</strong>: kabllot që nuk ju duhen hiqen, dhe kasa mbetet më e pastër.</li>
</ul>
<p>Nuk jeni të sigurt sa W ju duhen? <a href="/kontakt">Na kontaktoni</a>, ose <a href="/pc-me-porosi">na lini ta zgjedhim ne</a> si pjesë të një PC-je me porosi.</p>'
WHERE slug = 'furnizues-energjie' AND intro_html IS NULL;

UPDATE category SET intro_html = '<p>Kasa kompjuteri të reja dhe të përdorura në Tiranë. Te çdo kasë gjeni formatin, ventilatorët e përfshirë dhe portat përpara.</p>
<ul>
<li><strong>Gjatësia e kartës grafike</strong>: kontrolloni gjatësinë maksimale që pranon kasa.</li>
<li><strong>Lartësia e ftohësit</strong>: ftohësit e ajrit me kullë kërkojnë hapësirë, ftohja me ujë kërkon vend për radiatorin.</li>
<li><strong>Qarkullimi i ajrit</strong>: një panel përpara me rrjetë dhe disa ventilatorë e mbajnë kompjuterin më të ftohtë dhe më të qetë.</li>
</ul>
<p>Pagesë në dorëzim, dërgesë në gjithë Shqipërinë ose marrje falas në Tiranë, ku mund ta shihni dhe ta kontrolloni para se të paguani.</p>'
WHERE slug = 'kasa' AND intro_html IS NULL;

UPDATE category SET intro_html = '<p>Ftohës procesori me ajër dhe ftohje me ujë (AIO) në Tiranë, për AMD dhe Intel. Te çdo produkt gjeni soketet që mbështet, lartësinë e ftohësit ose madhësinë e radiatorit.</p>
<ul>
<li><strong>Soketi</strong>: ftohësi duhet të mbështesë soketin e <a href="/kategori/procesore">procesorit</a> (p.sh. AM4, AM5, LGA1700).</li>
<li><strong>Ajër apo ujë</strong>: një ftohës i mirë ajri mjafton për shumicën e procesorëve; një AIO 240–360 mm ndihmon për procesorët që nxehen më shumë dhe e mban kompjuterin më të pastër nga brenda.</li>
<li><strong>Hapësira</strong>: kontrolloni lartësinë maksimale të ftohësit ose vendin për radiatorin në <a href="/kategori/kasa">kasë</a>.</li>
</ul>
<p>Pagesë në dorëzim dhe dërgesë në gjithë Shqipërinë.</p>'
WHERE slug = 'ftohje' AND intro_html IS NULL;

UPDATE category SET intro_html = '<p>Monitorë të rinj dhe të përdorur në Tiranë, për gaming dhe punë. Te çdo monitor gjeni madhësinë, rezolucionin, frekuencën (Hz) dhe llojin e panelit.</p>
<ul>
<li><strong>Rezolucioni</strong>: 1080p për 24 inç dhe 1440p për 27 inç janë zgjedhjet më të balancuara për gaming.</li>
<li><strong>Frekuenca</strong>: 144 Hz e lart e bëjnë lojën shumë më të rrjedhshme, por kërkojnë një <a href="/kategori/karta-grafike">kartë grafike</a> që i nxjerr ato FPS.</li>
<li><strong>Paneli</strong>: IPS për ngjyra të sakta, VA për kontrast, TN për çmim më të ulët.</li>
</ul>
<p>Pagesë në dorëzim dhe dërgesë në gjithë Shqipërinë.</p>'
WHERE slug = 'monitore' AND intro_html IS NULL;

UPDATE category SET intro_html = '<p>PC gaming të gatshëm në Tiranë, të montuar dhe të testuar nga ne. Te çdo PC shkruajmë procesorin, kartën grafike, RAM-in, diskun dhe furnizuesin, që të dini saktë çfarë merrni.</p>
<p>Zgjidhni sipas buxhetit dhe lojërave që luani: filtrat në këtë faqe ju lejojnë të zgjidhni sipas buxhetit, kartës grafike dhe RAM-it. Nëse nuk gjeni atë që kërkoni, ju ndërtojmë një <a href="/pc-me-porosi">PC me porosi</a> sipas buxhetit tuaj, ose ndërroni kartën e vjetër me <a href="/nderro">Nderro</a>.</p>
<p>Pagesë në dorëzim, dërgesë në gjithë Shqipërinë ose marrje falas në Tiranë, ku mund ta shihni dhe ta kontrolloni para se të paguani.</p>'
WHERE slug = 'pc-te-gatshem' AND intro_html IS NULL;
