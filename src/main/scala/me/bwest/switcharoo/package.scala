package me.bwest

import zio.Has

package object switcharoo {
  type Switcharoo = Has[Switcharoo.Service]
}
