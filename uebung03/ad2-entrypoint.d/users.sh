#!/bin/sh

samba-tool user add helga 'hElga???' --given-name=Helga --surname=Beimer
samba-tool user add hans 'hAns???'   --given-name=Hans  --surname=Beimer
samba-tool user add klaus 'K1as???'  --given-name=Klaus --surname=Beimer
samba-tool user add else 'E1se???'   --given-name=Else  --surname=Kling
samba-tool user add olaf 'O1af???'   --given-name=Olaf  --surname=Kling
samba-tool group add Lindenstrasse
samba-tool group addmembers Lindenstrasse helga,hans,klaus,else,olaf
