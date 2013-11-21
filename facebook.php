<?php
/**
 * Copyright 2011 Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

include 'config.php';
// Create our Application instance (replace this with your appId and secret).

// Get User ID
$user = $facebook->getUser();

// We may or may not have this data based on whether the user is logged in.
//
// If we have a $user id here, it means we know the user is logged into
// Facebook, but we don't know if the access token is valid. An access
// token is invalid if the user logged out of Facebook.

if ($user) {
  try {
    // Proceed knowing you have a logged in user who's authenticated.
    $user_profile = $facebook->api('/me/friends');
  } catch (FacebookApiException $e) {
    error_log($e);
    $user = null;
  }
}

// Login or logout url will be needed depending on current user state.
if ($user) {
  $logoutUrl = "logout.php";
} else {
  $statusUrl = $facebook->getLoginStatusUrl();
  $loginUrl = $facebook->getLoginUrl(array('scope' => 'read_friendlists'));
}


?>
<!doctype html>
<html xmlns:fb="http://www.facebook.com/2008/fbml">
  <head>
    <title>php-sdk</title>
    <style>
      body {
        font-family: 'Lucida Grande', Verdana, Arial, sans-serif;
      }
      h1 a {
        text-decoration: none;
        color: #3b5998;
      }
      h1 a:hover {
        text-decoration: underline;
      }
    </style>
  </head>
  <body>
    <div style="margin-top:10%; width:70%; margin-left:auto; margin-right:auto;">
    <?php if (!$user): ?>
      <meta http-equiv="refresh" content="0; url=<?php echo $loginUrl; ?>">
      <center><h1>Please wait..</h1></center>
      <?php
        $_SESSION['user'] = $_GET['user'];
        exit(0); ?>
    <?php endif ?>
    <?php
        $token = $facebook->getAccessToken();
        if (isset($token)) {
            //print_r($_SESSION);
            $m = new Memcached();
            $m->addServer('localhost', 11211);
            $key = $m->get($_SESSION['user'] . '_key');
            if ($m->getResultCode() == Memcached::RES_NOTFOUND) {
                     echo "<center><h1>Couldn't find key, try connecting in Minecraft again? :(</h1></center>";
                     exit(0);
            }
            $user_profile = $facebook->api('/me/');
            $m->set($_SESSION['user'] . '_name', $user_profile['name'] , 3600);
            $m->set($_SESSION['user'] . '_fbID', $user_profile['id'] , 3600);
            unset($_SESSION['user']);
        }
        else {
            echo "<h2>Couldn't get token :(</h2>";
        }
    ?>
    <center>
        <h3>Type this into Minecraft</h3>
        <h1 style='font-family:"Lucida Console", Monaco, monospace'>/auth <?php echo $key; ?></h1>
  </center>
  </div>
  </body>
</html>
