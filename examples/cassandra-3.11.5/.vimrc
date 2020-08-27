syntax on

set number
set cursorline
set ruler
set shiftwidth=2
set softtabstop=2
set tabstop=2

set linebreak
set showmatch

set hlsearch
set smartcase
set ignorecase
set incsearch

set autoindent
set cindent
set smartindent
filetype plugin indent on

set undolevels=100
set backspace=indent,eol,start

set scrolloff=10

set colorcolumn=81

set list
set listchars=tab:\|\ 

set laststatus=2

if has("autocmd")
  au BufReadPost * if line("'\"") > 1 && line("'\"") <= line("$") | exe "normal! g'\"" | endif
endif
