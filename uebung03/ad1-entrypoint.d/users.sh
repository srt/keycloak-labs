#!/bin/sh

samba-tool  user add marge 'mArge!!!' --given-name=Marge --surname=Simpson
samba-tool  user add lisa 'l1sa!!!'   --given-name=Lisa  --surname=Simpson
samba-tool  user add homer 'h0mer!!!' --given-name=Homer --surname=Simpson
samba-tool  user add bart 'bArt!!!'   --given-name=Bart  --surname=Simpson
samba-tool group add Simpsons
samba-tool group addmembers Simpsons marge,lisa,homer,bart

samba-tool  user add leela  'lEela!!!'  --given-name=Leela  --mail-address=leela@futurama.example.com
samba-tool  user add bender 'bEnder!!!' --given-name=Bender --mail-address=bender@futurama.example.com
samba-tool group add Futurama
samba-tool group addmembers Futurama leela,bender
